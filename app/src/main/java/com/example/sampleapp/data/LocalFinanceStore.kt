package com.example.sampleapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class MpesaTx(
    val id: String,
    val direction: String,
    val amount: Double,
    val counterparty: String,
    val category: String,
    val timestamp: Long
)

object LocalFinanceStore {
    private const val PREF_NAME = "safepesa_offline_vault"
    private const val KEY_TXS = "transactions"

    fun saveTransaction(context: Context, tx: MpesaTx) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = getAllTransactions(context).toMutableList()
        if (existing.any { it.id == tx.id }) return

        existing.add(0, tx)
        saveList(prefs, existing)
    }

    fun updateCategory(context: Context, txId: String, newCategory: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = getAllTransactions(context).map {
            if (it.id == txId) it.copy(category = newCategory) else it
        }
        saveList(prefs, updated)
    }

    private fun saveList(prefs: SharedPreferences, list: List<MpesaTx>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("direction", it.direction)
                put("amount", it.amount)
                put("counterparty", it.counterparty)
                put("category", it.category)
                put("timestamp", it.timestamp)
            })
        }
        prefs.edit().putString(KEY_TXS, array.toString()).apply()
    }

    fun getAllTransactions(context: Context): List<MpesaTx> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_TXS, "[]") ?: "[]"
        val array = JSONArray(raw)
        val result = mutableListOf<MpesaTx>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(
                MpesaTx(
                    id = obj.getString("id"),
                    direction = obj.getString("direction"),
                    amount = obj.getDouble("amount"),
                    counterparty = obj.getString("counterparty"),
                    category = obj.getString("category"),
                    timestamp = obj.getLong("timestamp")
                )
            )
        }
        return result
    }

    fun getTotalGamblingLoss(context: Context): Double {
        return getAllTransactions(context)
            .filter { it.category == "GAMBLING" && it.direction == "OUT" }
            .sumOf { it.amount }
    }

    fun generateFinancialAdvice(context: Context): String {
        val txs = getAllTransactions(context)
        val totalIn = txs.filter { it.direction == "IN" }.sumOf { it.amount }
        val totalOut = txs.filter { it.direction == "OUT" }.sumOf { it.amount }
        val gamblingLoss = getTotalGamblingLoss(context)
        val unclassified = txs.count { it.category == "UNCLASSIFIED" }

        return when {
            gamblingLoss > 0 && totalIn > 0 && (gamblingLoss / totalIn) > 0.2 ->
                "Alert: You spent Ksh ${"%,.0f".format(gamblingLoss)} (${((gamblingLoss / totalIn) * 100).toInt()}% of income) on betting. Redirecting that to an MMF at 12% p.a. would transform your savings."
            gamblingLoss > 0 ->
                "You have lost Ksh ${"%,.0f".format(gamblingLoss)} to betting so far. Keeping the blocker active protects every shilling you earn."
            unclassified > 0 ->
                "You have $unclassified untagged M-Pesa transaction(s). Describe them below so I can analyze your spending."
            totalIn > totalOut && totalIn > 0 ->
                "Strong cash flow! Net surplus of Ksh ${"%,.0f".format(totalIn - totalOut)}. Consider moving 20% into a locked savings account."
            else ->
                "Offline M-Pesa monitor active. New M-Pesa messages will appear below for you to describe."
        }
    }
    private const val KEY_PARTNER_PIN = "partner_pin"
    private const val KEY_UNLOCK_UNTIL = "settings_unlock_until"

    fun setPartnerPin(context: Context, pin: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_PARTNER_PIN, pin).apply()
    }

    fun hasPartnerPin(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return !prefs.getString(KEY_PARTNER_PIN, null).isNullOrEmpty()
    }

    fun verifyAndTemporarilyUnlock(context: Context, enteredPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PARTNER_PIN, null) ?: return false
        if (savedPin == enteredPin) {
            // Unlock Settings access for 60 seconds so the partner can manage permissions
            prefs.edit().putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + 60_000L).apply()
            return true
        }
        return false
    }

    fun isTemporarilyUnlocked(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return System.currentTimeMillis() < prefs.getLong(KEY_UNLOCK_UNTIL, 0L)
    }
}
