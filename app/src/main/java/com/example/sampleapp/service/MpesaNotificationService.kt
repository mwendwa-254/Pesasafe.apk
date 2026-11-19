package com.example.sampleapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.sampleapp.data.LocalFinanceStore
import com.example.sampleapp.data.MpesaTx
import java.util.regex.Pattern

class MpesaNotificationService : NotificationListenerService() {

    private val gamblingKeywords = listOf(
        "sportpesa", "betika", "odibets", "mozzart", "1xbet", "betway",
        "melbet", "paripesa", "helabet", "bangbet", "shabiki", "aviator",
        "pepesha", "hakibets", "kwikbet", "betpawa", "22bet", "betlion",
        "290290", "290500", "290680", "290020", "290007", "290111"
    )

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Automatically ask Android to reconnect the listener if the system drops it
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(ComponentName(this, MpesaNotificationService::class.java))
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val shortText = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""

        // Prefer bigText (full SMS body in Google Messages/Samsung Messages) over truncated shortText
        val fullBody = if (bigText.length >= shortText.length) bigText else shortText
        if (fullBody.isEmpty()) return

        val lowerTitle = title.lowercase()
        val lowerBody = fullBody.lowercase()

        // Match either by sender title ("MPESA" / "M-PESA") OR by M-Pesa message signature ("confirmed." + "ksh")
        val isMpesaSender = lowerTitle.contains("mpesa") || lowerTitle.contains("m-pesa")
        val isMpesaBody = lowerBody.contains("confirmed") && lowerBody.contains("ksh")

        if (!isMpesaSender && !isMpesaBody) return

        parseAndSaveMpesa(fullBody)
    }

    private fun parseAndSaveMpesa(msg: String) {
        // 1. Extract 10-character transaction code (e.g. SL82K9X12P Confirmed)
        val codeMatcher = Pattern.compile("\\b([A-Z0-9]{10})\\s+Confirmed", Pattern.CASE_INSENSITIVE).matcher(msg)
        val txId = if (codeMatcher.find()) {
            codeMatcher.group(1)?.uppercase() ?: return
        } else {
            // Fallback ID if message format is unusual
            "TX${System.currentTimeMillis() % 100000000}"
        }

        // 2. Extract first Ksh amount (supports both "Ksh500.00" and "Ksh 500.00")
        val amountMatcher = Pattern.compile("Ksh\\s*([0-9,]+(?:\\.\\d{1,2})?)", Pattern.CASE_INSENSITIVE).matcher(msg)
        val amount = if (amountMatcher.find()) {
            amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        if (amount <= 0.0) return

        // 3. Determine Money IN vs Money OUT
        val lowerMsg = msg.lowercase()
        val isIncoming = lowerMsg.contains("you have received") ||
                lowerMsg.contains("give ksh") ||
                lowerMsg.contains("deposited") ||
                lowerMsg.contains("reversed")
        val direction = if (isIncoming) "IN" else "OUT"

        // 4. Flag gambling deposits automatically
        val isGambling = !isIncoming && gamblingKeywords.any { lowerMsg.contains(it) }
        val category = if (isGambling) "GAMBLING" else "UNCLASSIFIED"

        // 5. Extract counterparty (person, Paybill, or Till name)
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

        val tx = MpesaTx(txId, direction, amount, counterparty, category, System.currentTimeMillis())
        LocalFinanceStore.saveTransaction(this, tx)

        if (!isGambling) {
            promptUserToDescribeTransaction(tx)
        }
    }

    private fun promptUserToDescribeTransaction(tx: MpesaTx) {
        val channelId = "mpesa_advisor_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "M-Pesa Financial Advisor", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val categories = if (tx.direction == "IN") listOf("SALARY", "HUSTLE", "BUSINESS")
        else listOf("FOOD", "RENT/BILLS", "TRANSPORT")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("M-Pesa ${tx.direction}: Ksh ${"%,.0f".format(tx.amount)}")
            .setContentText("Describe this (${tx.counterparty}):")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        categories.forEachIndexed { index, cat ->
            val intent = Intent(this, TagTransactionReceiver::class.java).apply {
                putExtra("TX_ID", tx.id)
                putExtra("CATEGORY", cat)
                putExtra("NOTIF_ID", tx.id.hashCode())
            }
            val pending = PendingIntent.getBroadcast(
                this, tx.id.hashCode() + index, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, cat, pending)
        }

        manager.notify(tx.id.hashCode(), builder.build())
    }
}

class TagTransactionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val txId = intent.getStringExtra("TX_ID") ?: return
        val category = intent.getStringExtra("CATEGORY") ?: return
        val notifId = intent.getIntExtra("NOTIF_ID", 0)

        LocalFinanceStore.updateCategory(context, txId, category)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notifId)
    }
}
