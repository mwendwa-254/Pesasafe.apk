package com.example.sampleapp.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GamblingBlockerService : AccessibilityService() {

    private var lastTriggerTime = 0L

    // Comprehensive list of Kenyan betting operators & global crash games
    private val blockedKeywords = listOf(
        // Crash & Instant Games
        "aviator", "aviatrix", "jetx", "lucky jet", "spaceman", "zeppelin",
        "rocketman", "rocket queen", "crashx", "crash game", "big bass crash",
        "high flyer", "plinko", "mines", "chicken road", "penalty shoot",
        "spribe", "smartsoft", "turbo games", "evoplay", "galaxsys",
        // Kenyan & African Betting Sites
        "betika", "odibets", "sportpesa", "mozzartbet", "1xbet", "22bet",
        "betway", "melbet", "paripesa", "helabet", "bangbet", "shabiki",
        "hakibets", "kwikbet", "betlion", "chezacash", "maybets", "kilibet",
        "palmsbet", "betpawa", "meridianbet", "bongobongo", "playmaster",
        "winpesa", "mcheza", "gamemania", "scorepesa", "pesaplay"
    )

    // Hidden URL patterns used when sites mask crash games (e.g. /game-2, /casino/crash)
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

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return

        // 1. Check the browser URL / search bar
        for (viewId in addressBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val urlText = node.text?.toString()?.lowercase()?.trim() ?: continue
                if (urlText.isEmpty() || urlText.startsWith("search or type")) continue

                val isMatch = blockedKeywords.any { urlText.contains(it) } ||
                        hiddenGameUrlPatterns.any { urlText.contains(it) }

                if (isMatch) {
                    closeExactGamblingTab(urlText)
                    return
                }
            }
        }

        // 2. Check loaded webpage content (catches sites where URL has zero betting words)
        // Only runs when a webpage is actually open (url_bar is present) so Google search autocomplete is safe
        val hasActiveUrlBar = addressBarIds.any { rootNode.findAccessibilityNodeInfosByViewId(it).isNotEmpty() }
        if (hasActiveUrlBar && scanLoadedPageForCrashGames(rootNode, 0)) {
            closeExactGamblingTab("")
        }
    }

    private fun scanLoadedPageForCrashGames(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 12) return false
        val id = node.viewIdResourceName ?: ""
        // Skip the URL bar itself (already checked) and toolbar menus
        if (id.contains("url_bar") || id.contains("toolbar")) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        // Specific crash-game UI phrases that appear inside loaded game lobbies
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

    private fun closeExactGamblingTab(matchedUrl: String) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2200) return
        lastTriggerTime = now

        // First press Back to exit fullscreen game canvas / address bar edit mode
        performGlobalAction(GLOBAL_ACTION_BACK)

        Handler(Looper.getMainLooper()).postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            val tabSwitcher = currentRoot.findAccessibilityNodeInfosByViewId("com.android.chrome:id/tab_switcher_button")

            if (!tabSwitcher.isNullOrEmpty()) {
                tabSwitcher[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)

                Handler(Looper.getMainLooper()).postDelayed({
                    val switcherRoot = rootInActiveWindow ?: return@postDelayed
                    // Find the SPECIFIC tab card matching gambling keywords instead of blindly closing index 0
                    if (!closeMatchingTabCard(switcherRoot)) {
                        // If tab title wasn't readable, simply press Back to exit the switcher safely
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

        // Chrome's close button on each tab has contentDescription like "Close Aviator tab" or "Close Betika tab"
        if (desc.startsWith("close ") && desc.endsWith(" tab")) {
            if (blockedKeywords.any { desc.contains(it) }) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            }
        }

        // Also check parent card if title text matches a gambling keyword
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
