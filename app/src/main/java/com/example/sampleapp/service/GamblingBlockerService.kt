package com.example.sampleapp.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GamblingBlockerService : AccessibilityService() {

    private var lastTriggerTime = 0L

    private val blockedKeywords = listOf(
        "aviator", "jetx", "spaceman", "spribe", "crash game",
        "betika", "odibets", "sportpesa", "mozzartbet", "1xbet",
        "betway", "melbet", "bangbet", "helabet", "shabiki", "hakibets"
    )

    // Known browser address bar view IDs
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

        for (viewId in addressBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val urlText = node.text?.toString()?.lowercase()?.trim() ?: continue
                if (urlText.isEmpty() || urlText.startsWith("search or type")) continue

                if (blockedKeywords.any { urlText.contains(it) }) {
                    closeCurrentTabSilently(rootNode)
                    return
                }
            }
        }
    }

    private fun closeCurrentTabSilently(rootNode: AccessibilityNodeInfo) {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2000) return
        lastTriggerTime = now

        // Step 1: Back out of the address bar / loading page
        performGlobalAction(GLOBAL_ACTION_BACK)

        // Step 2: Open Chrome's Tab Switcher and click the 'X' close button on the tab
        Handler(Looper.getMainLooper()).postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            val tabSwitcher = currentRoot.findAccessibilityNodeInfosByViewId("com.android.chrome:id/tab_switcher_button")

            if (!tabSwitcher.isNullOrEmpty()) {
                tabSwitcher[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)

                Handler(Looper.getMainLooper()).postDelayed({
                    val switcherRoot = rootInActiveWindow ?: return@postDelayed
                    val closeButtons = switcherRoot.findAccessibilityNodeInfosByViewId("com.android.chrome:id/action_button")
                    if (!closeButtons.isNullOrEmpty()) {
                        // Closes the active gambling tab while keeping Chrome open
                        closeButtons[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    }
                }, 300)
            }
        }, 250)
    }

    override fun onInterrupt() {}
}
