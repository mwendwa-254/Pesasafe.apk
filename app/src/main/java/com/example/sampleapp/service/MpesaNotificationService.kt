package com.example.sampleapp.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
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
        "pepesha", "hakibets", "kwikbet", "290290", "290500", "290680", "290020"
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn?.notification ?: return
        val extras = notification.extras
        val title = extras.getString("android.title")?.lowercase() ?: ""
        val text = extras.getString("android.text") ?: return

        if (!title.contains("mpesa") && !title.contains("m-pesa") && !text.contains("Confirmed.")) return
        parseAndSaveMpesa(text)
    }

    private fun parseAndSaveMpesa(msg: String) {
        val codeMatcher = Pattern.compile("^([A-Z0-9]{10})\\s+Confirmed").matcher(msg)
        val txId = if (codeMatcher.find()) codeMatcher.group(1) ?: return else return

        val amountMatcher = Pattern.compile("Ksh([0-9,]+\\.\\d{2})").matcher(msg)
        val amount = if (amountMatcher.find()) {
            amountMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        } else 0.0

        val isIncoming = msg.contains("You have received", ignoreCase = true) ||
                msg.contains("Give Ksh", ignoreCase = true)
        val direction = if (isIncoming) "IN" else "OUT"

        val lowerMsg = msg.lowercase()
        val isGambling = !isIncoming && gamblingKeywords.any { lowerMsg.contains(it) }
        val category = if (isGambling) "GAMBLING" else "UNCLASSIFIED"

        val toRegex = Pattern.compile("(?:sent to|paid to|from)\\s+([A-Za-z0-9\\s.&-]+?)(?:\\s+on|\\s+for|\\.\\s)").matcher(msg)
        val counterparty = if (toRegex.find()) toRegex.group(1)?.trim() ?: "M-Pesa Transaction" else "M-Pesa Transaction"

        val tx = MpesaTx(txId, direction, amount, counterparty, category, System.currentTimeMillis())
        LocalFinanceStore.saveTransaction(this, tx)

        if (!isGambling) promptUserToDescribeTransaction(tx)
    }

    private fun promptUserToDescribeTransaction(tx: MpesaTx) {
        val channelId = "mpesa_advisor_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "M-Pesa Financial Advisor", NotificationManager.IMPORTANCE_HIGH)
            )
        }

        val categories = if (tx.direction == "IN") listOf("SALARY/HUSTLE", "GIFT/LOAN", "BUSINESS")
        else listOf("FOOD/SHOPPING", "BILLS/RENT", "TRANSPORT")

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("M-Pesa ${tx.direction}: Ksh ${"%,.0f".format(tx.amount)}")
            .setContentText("Describe this transaction (${tx.counterparty}):")
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
