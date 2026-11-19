package com.example.sampleapp.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.sampleapp.PartnerPinLockActivity
import com.example.sampleapp.data.LocalFinanceStore

class GamblingBlockerService : AccessibilityService() {

    private var lastTriggerTime = 0L
    private var lastPageScanTime = 0L
    private var lastLockLaunchTime = 0L

    private val blockedKeywords = listOf(
        "aviator", "aviatrix", "jetx", "lucky jet", "spaceman", "zeppelin",
        "rocketman", "rocket queen", "crashx", "crash game", "big bass crash",
        "high flyer", "plinko", "mines", "chicken road", "penalty shoot",
        "spribe", "smartsoft", "turbo games", "evoplay", "galaxsys",
        "betika", "odibets", "sportpesa", "mozzartbet", "1xbet", "22bet",
        "betway", "melbet", "paripesa", "helabet", "bangbet", "shabiki",
        "hakibets", "kwikbet", "betlion", "chezacash", "maybets", "kilibet",
        "palmsbet", "betpawa", "meridianbet", "bongobongo", "playmaster",
        "winpesa", "mcheza", "gamemania", "scorepesa", "pesaplay"
    )

    private val hiddenGameUrlPatterns = listOf(
        "/game-2", "/game/2", "/crash", "/instant-win", "/virtuals",
        "/casino/play", "/live-casino", "/spribe", "/mini-games", "/turbo"
    )

    private val addressBarIds = listOf(
        "com.android.chrome:id/url_bar",
        "com.opera.browser:id/url_field",
        "com.opera.mini.native:id/url_field",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.brave.browser:id/url_bar"
    )

    private val browserPackages = listOf(
        "com.android.chrome",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.sec.android.app.sbrowser",
        "org.mozilla.firefox",
        "com.brave.browser"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        val pkgName = rootNode.packageName?.toString() ?: ""

        // 0. ACCOUNTABILITY GUARD: Runs on ANY non-browser/non-SafePesa system screen (catches all OEM Settings & Device Admin dialogs)
        if (pkgName !in browserPackages &&
            pkgName != packageName &&
            LocalFinanceStore.hasPartnerPin(this) &&
            !LocalFinanceStore.isTemporarilyUnlocked(this)
        ) {
            if (isTryingToTamperWithSafePesa(rootNode, 0)) {
                blockTamperingImmediately()
                return
            }
        }

        // 1. Check browser URL / search bar immediately
        for (viewId in addressBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val urlText = node.text?.toString()?.lowercase()?.trim() ?: continue
                if (urlText.isEmpty() || urlText.startsWith("search or type")) continue

                val isMatch = blockedKeywords.any { urlText.contains(it) } ||
                        hiddenGameUrlPatterns.any { urlText.contains(it) }

                if (isMatch) {
                    closeExactGamblingTab()
                    return
                }
            }
        }

        // 2. Throttled loaded webpage scan (every 1.2s to keep phone cool)
        val now = System.currentTimeMillis()
        if (now - lastPageScanTime > 1200) {
            lastPageScanTime = now
            val hasActiveUrlBar = addressBarIds.any { rootNode.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
            if (hasActiveUrlBar && scanLoadedPageForCrashGames(rootNode, 0)) {
                closeExactGamblingTab()
            }
        }
    }

    private fun blockTamperingImmediately() {
        val now = System.currentTimeMillis()
        if (now - lastLockLaunchTime < 1500) return
        lastLockLaunchTime = now

        // Force-exit Settings/Device Admin dialog straight to the Home screen (cannot be blocked by Android)
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)

        // Launch the Partner PIN Lock screen right after returning to Home
        Handler(Looper.getMainLooper()).postDelayed({
            val lockIntent = Intent(this, PartnerPinLockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(lockIntent)
        }, 200)
    }

    private fun isTryingToTamperWithSafePesa(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 14) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val tamperWords = listOf(
            "safepesa",
            "uninstall shield",
            "crash-game blocker",
            "m-pesa reader",
            "device admin",
            "deactivate",
            "accountability shield"
        )
        if (tamperWords.any { text.contains(it) || desc.contains(it) }) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (isTryingToTamperWithSafePesa(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun scanLoadedPageForCrashGames(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 12) return false
        val id = node.viewIdResourceName ?: ""
        if (id.contains("url_bar") || id.contains("toolbar")) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        val inGameTriggers = listOf(
            "place bet", "cash out", "auto cash out", "aviator", "jetx",
            "spaceman", "lucky jet", "plinko", "spribe", "provably fair"
        )
        if (inGameTriggers.any { text == it || desc == it || text.contains("place bet") || text.contains("cash out") }) {
            return true
        }

        for (i in 0 until node.childCount) {
            if (scanLoadedPageForCrashGames(node.getChild(i), depth + 1)) return true
        }
        return false
    }

    private fun closeExactGamblingTab() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2200) return
        lastTriggerTime = now

        performGlobalAction(GLOBAL_ACTION_BACK)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            val tabSwitcher = currentRoot.findAccessibilityNodeInfosByViewId("com.android.chrome:id/tab_switcher_button")

            if (!tabSwitcher.isNullOrEmpty()) {
                tabSwitcher[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)

                Handler(Looper.getMainLooper()).postDelayed({
                    val switcherRoot = rootInActiveWindow ?: return@postDelayed
                    if (!closeMatchingTabCard(switcherRoot)) {
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                }, 350)
            }
        }, 250)
    }

    private fun closeMatchingTabCard(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""

        if (desc.startsWith("close ") && desc.endsWith(" tab")) {
            if (blockedKeywords.any { desc.contains(it) }) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        if (blockedKeywords.any { text.contains(it) || desc.contains(it) }) {
            val parent = node.parent
            if (parent != null) {
                val closeBtns = parent.findAccessibilityNodeInfosByViewId("com.android.chrome:id/action_button")
                if (!closeBtns.isNullOrEmpty()) {
                    closeBtns[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }

        for (i in 0 until node.childCount) {
            if (closeMatchingTabCard(node.getChild(i))) return true
        }
        return false
    }

    override fun onInterrupt() {}
}
