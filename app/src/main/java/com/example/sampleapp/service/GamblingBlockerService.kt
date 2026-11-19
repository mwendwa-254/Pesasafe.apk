package com.example.sampleapp.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.sampleapp.data.LocalFinanceStore
import java.util.Locale

class GamblingBlockerService : AccessibilityService(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var lastTriggerTime = 0L

    private val blockedKeywords = listOf(
        "aviator", "jetx", "spaceman", "spribe", "crash game",
        "betika", "odibets", "sportpesa", "mozzartbet", "1xbet",
        "betway", "melbet", "bangbet", "helabet", "shabiki", "hakibets"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val rootNode = rootInActiveWindow ?: return
        if (containsGamblingContent(rootNode)) triggerIntervention()
    }

    private fun containsGamblingContent(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (blockedKeywords.any { text.contains(it) || desc.contains(it) }) return true

        for (i in 0 until node.childCount) {
            if (containsGamblingContent(node.getChild(i))) return true
        }
        return false
    }

    private fun triggerIntervention() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2500) return
        lastTriggerTime = now

        val totalLost = LocalFinanceStore.getTotalGamblingLoss(this)
        val warningMsg = if (totalLost > 0) {
            "Stop! Crash games are blocked. You have already lost ${totalLost.toInt()} shillings to betting."
        } else {
            "Stop! Gambling and crash games are blocked to protect your money."
        }

        tts?.speak(warningMsg, TextToSpeech.QUEUE_FLUSH, null, "BLOCK_ALERT")

        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, warningMsg, Toast.LENGTH_LONG).show()
        }

        performGlobalAction(GLOBAL_ACTION_BACK)
        Handler(Looper.getMainLooper()).postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)
        }, 250)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}
