package com.example.sampleapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.sampleapp.QuickTagPopupActivity
import com.example.sampleapp.data.LocalFinanceStore
import com.example.sampleapp.data.MpesaTx
import java.util.regex.Pattern

class MpesaSmsReceiver : BroadcastReceiver() {

    private val gamblingKeywords = listOf(
        "sportpesa", "betika", "odibets", "mozzart", "1xbet", "betway",
        "melbet", "paripesa", "helabet", "bangbet", "shabiki", "aviator",
        "pepesha", "hakibets", "kwikbet", "betpawa", "22bet", "betlion",
        "290290", "290500", "290680", "290020", "290007", "290111"
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val fullBody = StringBuilder()
        var sender = ""

        for (sms in messages) {
            sender = sms.displayOriginatingAddress ?: ""
            fullBody.append(sms.messageBody ?: "")
        }

        val msg = fullBody.toString().trim()
        if (!sender.equals("MPESA", ignoreCase = true) &&
            !(msg.lowercase().contains("confirmed") && msg.lowercase().contains("ksh"))
        ) {
            return
        }

        processMpesaMessage(context, msg)
    }

    companion object {
        fun processMpesaMessage(context: Context, msg: String) {
            val gamblingKeywords = listOf(
                "sportpesa", "betika", "odibets", "mozzart", "1xbet", "betway",
                "melbet", "paripesa", "helabet", "bangbet", "shabiki", "aviator",
                "pepesha", "hakibets", "kwikbet", "betpawa", "22bet", "betlion",
                "290290", "290500", "290680", "290020", "290007", "290111"
            )

            val codeMatcher = Pattern.compile("\\b([A-Z0-9]{10})\\s+Confirmed", Pattern.CASE_INSENSITIVE).matcher(msg)
            val txId = if (codeMatcher.find()) {
                codeMatcher.group(1)?.uppercase() ?: return
            } else {
                "TX${msg.hashCode()}"
            }

            val amountMatcher = Pattern.compile("Ksh\\s*([0-9,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(msg)
            val amount = if (amountMatcher.find()) {
                amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
            } else 0.0
            if (amount <= 0.0) return

            val lowerMsg = msg.lowercase()
            val isIncoming = lowerMsg.contains("you have received") ||
                    lowerMsg.contains("give ksh") ||
                    lowerMsg.contains("deposited") ||
                    lowerMsg.contains("reversed")
            val direction = if (isIncoming) "IN" else "OUT"

            val isGambling = !isIncoming && gamblingKeywords.any { lowerMsg.contains(it) }
            val category = if (isGambling) "GAMBLING" else "UNCLASSIFIED"

            val toRegex = Pattern.compile(
                "(?:sent to|paid to|from|bought airtime|withdraw\\s+ksh[0-9,.]+\\s+from)\\s+([A-Za-z0-9\\s.&'-]+?)(?:\\s+on|\\s+for|\\s+new|\\.|\\n|$)",
                Pattern.CASE_INSENSITIVE
            ).matcher(msg)
            val counterparty = if (toRegex.find()) {
                toRegex.group(1)?.trim()?.take(40) ?: "M-Pesa Transaction"
            } else if (lowerMsg.contains("airtime")) {
                "Airtime Purchase"
            } else {
                "M-Pesa Transaction"
            }

            val tx = MpesaTx(txId, direction, amount, counterparty, category, System.currentTimeMillis(), "MPESA")
            val isNew = LocalFinanceStore.saveTransaction(context, tx)

            // Launch instant floating pop-up right away if this is a new non-gambling transaction
            if (isNew && !isGambling) {
                val popupIntent = Intent(context, QuickTagPopupActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("TX_ID", tx.id)
                    putExtra("DIRECTION", tx.direction)
                    putExtra("AMOUNT", tx.amount)
                    putExtra("COUNTERPARTY", tx.counterparty)
                }
                context.startActivity(popupIntent)
            }
        }
    }
}
