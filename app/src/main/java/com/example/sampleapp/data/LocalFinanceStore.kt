package com.example.sampleapp.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.concurrent.TimeUnit

data class MpesaTx(
    val id: String,
    val direction: String, // "IN" or "OUT"
    val amount: Double,
    val counterparty: String,
    val category: String,
    val timestamp: Long,
    val source: String = "MPESA" // "MPESA" or "BANK"
)

data class DebtItem(
    val id: String,
    val lender: String,
    val totalAmount: Double,
    val remainingAmount: Double,
    val dueDateMillis: Long,
    val isPaid: Boolean = false,
    val paidVia: String = "" // "MPESA", "BANK", "CASH"
)

data class MonthlyBill(
    val id: String,
    val name: String,
    val amount: Double,
    val dueDayOfMonth: Int,
    val isPaidThisMonth: Boolean = false,
    val paidVia: String = ""
)

object LocalFinanceStore {
    private const val PREF_NAME = "safepesa_offline_vault"
    private const val KEY_TXS = "transactions"
    private const val KEY_DEBTS = "debts"
    private const val KEY_BILLS = "monthly_bills"
    private const val KEY_PAY_CYCLE = "pay_cycle" // "DAILY", "WEEKLY", "MONTHLY"
    private const val KEY_PARTNER_PIN = "partner_pin"
    private const val KEY_UNLOCK_UNTIL = "settings_unlock_until"

    // --- PAY CYCLE (DAILY / WEEKLY / MONTHLY) ---
    fun setPayCycle(context: Context, cycle: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PAY_CYCLE, cycle).apply()
    }

    fun getPayCycle(context: Context): String {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PAY_CYCLE, "MONTHLY") ?: "MONTHLY"
    }

    // --- TRANSACTIONS (M-PESA & BANK) ---
    fun saveTransaction(context: Context, tx: MpesaTx): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val existing = getAllTransactions(context).toMutableList()
        if (existing.any { it.id == tx.id }) return false // Deduplicate SMS + Notification

        existing.add(0, tx)
        val array = JSONArray()
        existing.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("direction", it.direction)
                put("amount", it.amount)
                put("counterparty", it.counterparty)
                put("category", it.category)
                put("timestamp", it.timestamp)
                put("source", it.source)
            })
        }
        prefs.edit().putString(KEY_TXS, array.toString()).apply()
        return true
    }

    fun updateCategory(context: Context, txId: String, newCategory: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val updated = getAllTransactions(context).map {
            if (it.id == txId) it.copy(category = newCategory) else it
        }
        val array = JSONArray()
        updated.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("direction", it.direction)
                put("amount", it.amount)
                put("counterparty", it.counterparty)
                put("category", it.category)
                put("timestamp", it.timestamp)
                put("source", it.source)
            })
        }
        prefs.edit().putString(KEY_TXS, array.toString()).apply()
    }

    fun getAllTransactions(context: Context): List<MpesaTx> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val array = JSONArray(prefs.getString(KEY_TXS, "[]") ?: "[]")
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
                    timestamp = obj.getLong("timestamp"),
                    source = obj.optString("source", "MPESA")
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

    // --- DEBTS & SEVERITY ---
    fun addDebt(context: Context, debt: DebtItem) {
        val list = getAllDebts(context).toMutableList()
        list.add(0, debt)
        saveDebts(context, list)
    }

    fun markDebtPaid(context: Context, debtId: String, via: String) {
        val updated = getAllDebts(context).map {
            if (it.id == debtId) it.copy(isPaid = true, remainingAmount = 0.0, paidVia = via) else it
        }
        saveDebts(context, updated)
    }

    private fun saveDebts(context: Context, list: List<DebtItem>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("lender", it.lender)
                put("totalAmount", it.totalAmount)
                put("remainingAmount", it.remainingAmount)
                put("dueDateMillis", it.dueDateMillis)
                put("isPaid", it.isPaid)
                put("paidVia", it.paidVia)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEBTS, array.toString()).apply()
    }

    fun getAllDebts(context: Context): List<DebtItem> {
        val array = JSONArray(
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_DEBTS, "[]") ?: "[]"
        )
        val res = mutableListOf<DebtItem>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            res.add(
                DebtItem(
                    id = o.getString("id"),
                    lender = o.getString("lender"),
                    totalAmount = o.getDouble("totalAmount"),
                    remainingAmount = o.getDouble("remainingAmount"),
                    dueDateMillis = o.getLong("dueDateMillis"),
                    isPaid = o.optBoolean("isPaid", false),
                    paidVia = o.optString("paidVia", "")
                )
            )
        }
        return res
    }

    fun getDebtSeverity(debt: DebtItem): Pair<String, String> {
        if (debt.isPaid) return Pair("PAID (${debt.paidVia})", "#059669")
        val daysLeft = TimeUnit.MILLISECONDS.toDays(debt.dueDateMillis - System.currentTimeMillis())
        val isHighInterestApp = listOf("fuliza", "tala", "branch", "mshwari", "kcb", "zenka", "okash")
            .any { debt.lender.lowercase().contains(it) }

        return when {
            daysLeft < 0 -> Pair("OVERDUE (${-daysLeft}d late)", "#991B1B")
            daysLeft <= 3 || isHighInterestApp -> Pair("CRITICAL (${daysLeft}d left)", "#DC2626")
            daysLeft <= 14 -> Pair("HIGH (${daysLeft}d left)", "#D97706")
            else -> Pair("MODERATE (${daysLeft}d left)", "#2563EB")
        }
    }

    // --- MONTHLY BILLS ---
    fun addMonthlyBill(context: Context, bill: MonthlyBill) {
        val list = getMonthlyBills(context).toMutableList()
        list.add(0, bill)
        saveBills(context, list)
    }

    fun toggleBillPaid(context: Context, billId: String, paid: Boolean, via: String = "BANK/MPESA") {
        val updated = getMonthlyBills(context).map {
            if (it.id == billId) it.copy(isPaidThisMonth = paid, paidVia = if (paid) via else "") else it
        }
        saveBills(context, updated)
    }

    private fun saveBills(context: Context, list: List<MonthlyBill>) {
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("amount", it.amount)
                put("dueDayOfMonth", it.dueDayOfMonth)
                put("isPaidThisMonth", it.isPaidThisMonth)
                put("paidVia", it.paidVia)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_BILLS, array.toString()).apply()
    }

    fun getMonthlyBills(context: Context): List<MonthlyBill> {
        val array = JSONArray(
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_BILLS, "[]") ?: "[]"
        )
        val res = mutableListOf<MonthlyBill>()
        for (i in 0 until array.length()) {
            val o = array.getJSONObject(i)
            res.add(
                MonthlyBill(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    amount = o.getDouble("amount"),
                    dueDayOfMonth = o.getInt("dueDayOfMonth"),
                    isPaidThisMonth = o.optBoolean("isPaidThisMonth", false),
                    paidVia = o.optString("paidVia", "")
                )
            )
        }
        return res
    }

    // --- SMART BUDGETING, REPAYMENT PLAN & BAD HABIT ANALYZER ---
    fun generateComprehensiveAdvice(context: Context): String {
        val txs = getAllTransactions(context)
        val debts = getAllDebts(context).filter { !it.isPaid }
        val bills = getMonthlyBills(context).filter { !it.isPaidThisMonth }
        val payCycle = getPayCycle(context)

        val totalIn = txs.filter { it.direction == "IN" }.sumOf { it.amount }
        val gamblingLoss = getTotalGamblingLoss(context)
        val unpaidBillsTotal = bills.sumOf { it.amount }
        val unpaidDebtTotal = debts.sumOf { it.remainingAmount }

        val insights = mutableListOf<String>()

        // 1. Income Pacing & Allowance Advice
        val safeSpendable = (totalIn - unpaidBillsTotal - unpaidDebtTotal).coerceAtLeast(0.0)
        when (payCycle) {
            "MONTHLY" -> {
                val weeklyAllowance = safeSpendable / 4.0
                val dailyAllowance = safeSpendable / 30.0
                insights.add("📅 Monthly Plan: After reserving Ksh ${"%,.0f".format(unpaidBillsTotal)} for unpaid bills and Ksh ${"%,.0f".format(unpaidDebtTotal)} for debt, your safe spending cap is Ksh ${"%,.0f".format(weeklyAllowance)}/week (Ksh ${"%,.0f".format(dailyAllowance)}/day).")
            }
            "WEEKLY" -> {
                val dailyAllowance = safeSpendable / 7.0
                insights.add("📅 Weekly Plan: Keep daily spending under Ksh ${"%,.0f".format(dailyAllowance)}/day so your bills and debt targets stay covered.")
            }
            "DAILY" -> {
                val dailyDebtTarget = if (unpaidDebtTotal > 0) (unpaidDebtTotal / 14.0) else 0.0
                insights.add("📅 Daily Earner Rule: Set aside Ksh ${"%,.0f".format(dailyDebtTarget)}/day from today's hustle toward debt & monthly bills before spending on wants.")
            }
        }

        // 2. Debt Repayment Plan (Urgent First)
        val mostUrgentDebt = debts.minByOrNull { it.dueDateMillis }
        if (mostUrgentDebt != null) {
            val daysLeft = TimeUnit.MILLISECONDS.toDays(mostUrgentDebt.dueDateMillis - System.currentTimeMillis()).coerceAtLeast(1)
            val perDayNeeded = mostUrgentDebt.remainingAmount / daysLeft
            insights.add("🎯 Priority Debt: Pay '${mostUrgentDebt.lender}' (Ksh ${"%,.0f".format(mostUrgentDebt.remainingAmount)}) first—set aside Ksh ${"%,.0f".format(perDayNeeded)}/day to clear it before deadline.")
        }

        // 3. Bad Spending Habits Analyzer (Beyond Gambling)
        val outs = txs.filter { it.direction == "OUT" && it.category != "GAMBLING" }
        val totalLivingOut = outs.sumOf { it.amount }

        val airtimeSpend = outs.filter { it.category.contains("AIRTIME") || it.counterparty.lowercase().contains("airtime") }.sumOf { it.amount }
        val foodImpulseSpend = outs.filter { it.category.contains("FOOD") || it.category.contains("IMPULSE") || it.category.contains("ENTERTAINMENT") }.sumOf { it.amount }
        val lateNightSpend = outs.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.timestamp }
            val hr = cal.get(Calendar.HOUR_OF_DAY)
            hr >= 22 || hr <= 4
        }.sumOf { it.amount }

        if (gamblingLoss > 0) {
            insights.add("🚫 Betting Leak: Ksh ${"%,.0f".format(gamblingLoss)} lost to gambling—blocker is active.")
        }
        if (totalLivingOut > 0 && (airtimeSpend / totalLivingOut) > 0.18) {
            insights.add("⚠ Habit Alert (Airtime/Data): ${((airtimeSpend / totalLivingOut) * 100).toInt()}% of your spending (Ksh ${"%,.0f".format(airtimeSpend)}) went to airtime/bundles. Switch to a fixed weekly bundle.")
        }
        if (totalLivingOut > 0 && (foodImpulseSpend / totalLivingOut) > 0.35) {
            insights.add("⚠ Habit Alert (Food/Impulse): Ksh ${"%,.0f".format(foodImpulseSpend)} spent on food/impulse buys. Cooking at home can free up cash for your debts.")
        }
        if (lateNightSpend > 500) {
            insights.add("🌙 Late-Night Spending: You spent Ksh ${"%,.0f".format(lateNightSpend)} between 10 PM and 4 AM—high-risk hours for impulsive money decisions.")
        }

        return insights.joinToString("\n\n")
    }

    // --- PARTNER PIN & LOCK ---
    fun setPartnerPin(context: Context, pin: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_PARTNER_PIN, pin).apply()
    }

    fun hasPartnerPin(context: Context): Boolean {
        return !context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PARTNER_PIN, null).isNullOrEmpty()
    }

    fun verifyAndTemporarilyUnlock(context: Context, enteredPin: String): Boolean {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PARTNER_PIN, null) ?: return false
        if (savedPin == enteredPin) {
            prefs.edit().putLong(KEY_UNLOCK_UNTIL, System.currentTimeMillis() + 60_000L).apply()
            return true
        }
        return false
    }

    fun isTemporarilyUnlocked(context: Context): Boolean {
        return System.currentTimeMillis() < context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_UNLOCK_UNTIL, 0L)
    }
}
