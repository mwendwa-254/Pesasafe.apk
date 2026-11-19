package com.example.sampleapp

import android.Manifest
import android.app.DatePickerDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.sampleapp.data.*
import com.example.sampleapp.service.SafePesaAdminReceiver
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var netBalanceText: TextView
    private lateinit var moneyInText: TextView
    private lateinit var billsDebtText: TextView
    private lateinit var gamblingLossText: TextView
    private lateinit var adviceText: TextView
    private lateinit var billsContainer: LinearLayout
    private lateinit var debtsContainer: LinearLayout
    private lateinit var txContainer: LinearLayout
    private lateinit var cycleButtons: Map<String, TextView>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#064E3B")

        // Request SMS permission on startup so M-Pesa works regardless of default SMS app
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS), 101)
        }

        val scroll = ScrollView(this).apply { setBackgroundColor(Color.parseColor("#F1F5F9")) }
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(root)

        // 1. HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#064E3B"))
            setPadding(44, 48, 44, 52)
        }
        val topBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        topBar.addView(TextView(this).apply {
            text = "SafePesa"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        topBar.addView(TextView(this).apply {
            text = "100% OFFLINE"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A7F3D0"))
            background = makePill("#047857", 20f)
            setPadding(20, 8, 20, 8)
        })
        header.addView(topBar)

        header.addView(TextView(this).apply {
            text = "Safe Spendable Balance (After Unpaid Bills & Debts)"
            textSize = 12f
            setTextColor(Color.parseColor("#A7F3D0"))
            setPadding(0, 24, 0, 4)
        })
        netBalanceText = TextView(this).apply {
            text = "Ksh 0.00"
            textSize = 30f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        header.addView(netBalanceText)

        // Income Frequency Selector (DAILY | WEEKLY | MONTHLY)
        header.addView(TextView(this).apply {
            text = "How often do you earn?"
            textSize = 12f
            setTextColor(Color.parseColor("#D1FAE5"))
            setPadding(0, 20, 0, 8)
        })
        val cycleRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val cycles = listOf("DAILY", "WEEKLY", "MONTHLY")
        val btnMap = mutableMapOf<String, TextView>()
        cycles.forEachIndexed { idx, cycle ->
            val tv = TextView(this).apply {
                text = cycle
                textSize = 12f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    if (idx < 2) rightMargin = 12
                }
                setOnClickListener {
                    LocalFinanceStore.setPayCycle(this@MainActivity, cycle)
                    refreshDashboard()
                }
            }
            btnMap[cycle] = tv
            cycleRow.addView(tv)
        }
        cycleButtons = btnMap
        header.addView(cycleRow)
        root.addView(header)

        // BODY
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 28, 32, 48)
        }
        root.addView(body)

        // 2. PROTECTION & BANK ENTRY BUTTONS
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(createActionButton("M-Pesa Reader", "#0F766E") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        row1.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) })
        row1.addView(createActionButton("Crash Blocker", "#1E293B") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        body.addView(row1)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 0)
        }
        row2.addView(createActionButton("+ Log Bank / Cash", "#2563EB") {
            showBankTransactionDialog()
        })
        row2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) })
        row2.addView(createActionButton("🔒 Partner Lock", "#B45309") {
            showPartnerPinSetupDialog()
        })
        body.addView(row2)

        // 3. METRICS ROW
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 20)
        }
        moneyInText = TextView(this)
        billsDebtText = TextView(this)
        gamblingLossText = TextView(this)
        metricsRow.addView(createStatCard("TOTAL INCOME", moneyInText, "#ECFDF5", "#047857"))
        metricsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 1) })
        metricsRow.addView(createStatCard("UNPAID OBLIGATIONS", billsDebtText, "#EFF6FF", "#1D4ED8"))
        metricsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(12, 1) })
        metricsRow.addView(createStatCard("BETTING LOSS", gamblingLossText, "#FEF2F2", "#B91C1C"))
        body.addView(metricsRow)

        // 4. FINANCIAL ADVISOR & HABIT ANALYZER
        val advisorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makePill("#FFFFFF", 24f)
            setPadding(32, 28, 32, 28)
        }
        advisorCard.addView(TextView(this).apply {
            text = "Budget, Repayment & Habit Advisor"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#064E3B"))
        })
        adviceText = TextView(this).apply {
            textSize = 13.5f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 12, 0, 0)
            setLineSpacing(6f, 1f)
        }
        advisorCard.addView(adviceText)
        body.addView(advisorCard)

        // 5. MONTHLY FIXED BILLS SECTION
        body.addView(createSectionHeader("Monthly Fixed Bills", "+ Add Bill") { showAddBillDialog() })
        billsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(billsContainer)

        // 6. DEBT TRACKER & SEVERITY SECTION
        body.addView(createSectionHeader("Debt Tracker & Deadlines", "+ Add Debt") { showAddDebtDialog() })
        debtsContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(debtsContainer)

        // 7. TRANSACTION LEDGER
        body.addView(createSectionHeader("M-Pesa & Bank Ledger", "↻ Refresh") { refreshDashboard() })
        txContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(txContainer)

        setContentView(scroll)
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
        if (LocalFinanceStore.hasPartnerPin(this) && Settings.canDrawOverlays(this)) {
            val dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, SafePesaAdminReceiver::class.java)
            if (!dpm.isAdminActive(adminComponent)) {
                launchDeviceAdminPrompt()
            }
        }
    }

    private fun refreshDashboard() {
        val activeCycle = LocalFinanceStore.getPayCycle(this)
        cycleButtons.forEach { (cycle, btn) ->
            val selected = cycle == activeCycle
            btn.background = makePill(if (selected) "#FFFFFF" else "#047857", 18f)
            btn.setTextColor(if (selected) Color.parseColor("#064E3B") else Color.parseColor("#A7F3D0"))
        }

        val txs = LocalFinanceStore.getAllTransactions(this)
        val bills = LocalFinanceStore.getMonthlyBills(this)
        val debts = LocalFinanceStore.getAllDebts(this)

        val totalIn = txs.filter { it.direction == "IN" }.sumOf { it.amount }
        val totalLost = LocalFinanceStore.getTotalGamblingLoss(this)
        val unpaidObligations = bills.filter { !it.isPaidThisMonth }.sumOf { it.amount } +
                debts.filter { !it.isPaid }.sumOf { it.remainingAmount }
        val safeSpendable = (totalIn - unpaidObligations - totalLost).coerceAtLeast(0.0)

        netBalanceText.text = "Ksh ${"%,.2f".format(safeSpendable)}"
        moneyInText.text = "Ksh ${"%,.0f".format(totalIn)}"
        billsDebtText.text = "Ksh ${"%,.0f".format(unpaidObligations)}"
        gamblingLossText.text = "Ksh ${"%,.0f".format(totalLost)}"
        adviceText.text = LocalFinanceStore.generateComprehensiveAdvice(this)

        // Render Monthly Bills
        billsContainer.removeAllViews()
        if (bills.isEmpty()) {
            billsContainer.addView(emptyHint("No monthly bills set (e.g. Rent, KPLC, Water). Tap '+ Add Bill'."))
        } else {
            for (bill in bills) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = makePill("#FFFFFF", 20f)
                    setPadding(28, 22, 28, 22)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = "${bill.name} • Ksh ${"%,.0f".format(bill.amount)}"
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                })
                info.addView(TextView(this).apply {
                    text = if (bill.isPaidThisMonth) "✓ Paid (${bill.paidVia})" else "Due day ${bill.dueDayOfMonth} of month"
                    textSize = 12f
                    setTextColor(if (bill.isPaidThisMonth) Color.parseColor("#059669") else Color.parseColor("#D97706"))
                })
                card.addView(info)

                val action = TextView(this).apply {
                    text = if (bill.isPaidThisMonth) "Reset" else "Mark Paid"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = makePill(if (bill.isPaidThisMonth) "#64748B" else "#059669", 16f)
                    setPadding(24, 14, 24, 14)
                    setOnClickListener {
                        if (bill.isPaidThisMonth) {
                            LocalFinanceStore.toggleBillPaid(this@MainActivity, bill.id, false)
                            refreshDashboard()
                        } else {
                            showPaymentChannelDialog { via ->
                                LocalFinanceStore.toggleBillPaid(this@MainActivity, bill.id, true, via)
                                refreshDashboard()
                            }
                        }
                    }
                }
                card.addView(action)
                billsContainer.addView(card)
            }
        }

        // Render Debts
        debtsContainer.removeAllViews()
        if (debts.isEmpty()) {
            debtsContainer.addView(emptyHint("No debts tracked. Tap '+ Add Debt' to log Fuliza, loans, or personal debts."))
        } else {
            val dateFmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            for (debt in debts) {
                val (sevLabel, sevColor) = LocalFinanceStore.getDebtSeverity(debt)
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = makePill("#FFFFFF", 20f)
                    setPadding(28, 22, 28, 22)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 12 }
                }
                val info = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                info.addView(TextView(this).apply {
                    text = "${debt.lender} • Ksh ${"%,.0f".format(if (debt.isPaid) debt.totalAmount else debt.remainingAmount)}"
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#0F172A"))
                })
                info.addView(TextView(this).apply {
                    text = "$sevLabel • Due ${dateFmt.format(Date(debt.dueDateMillis))}"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(sevColor))
                })
                card.addView(info)

                if (!debt.isPaid) {
                    card.addView(TextView(this).apply {
                        text = "Mark Paid"
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.WHITE)
                        background = makePill("#059669", 16f)
                        setPadding(24, 14, 24, 14)
                        setOnClickListener {
                            showPaymentChannelDialog { via ->
                                LocalFinanceStore.markDebtPaid(this@MainActivity, debt.id, via)
                                refreshDashboard()
                            }
                        }
                    })
                }
                debtsContainer.addView(card)
            }
        }

        // Render Ledger
        txContainer.removeAllViews()
        if (txs.isEmpty()) {
            txContainer.addView(emptyHint("No transactions yet. M-Pesa messages and Bank entries will appear here."))
        } else {
            val timeFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
            for (tx in txs) {
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = makePill("#FFFFFF", 20f)
                    setPadding(28, 24, 28, 24)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = 14 }
                }
                val topRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val isIn = tx.direction == "IN"
                topRow.addView(TextView(this).apply {
                    text = "${if (isIn) "+" else "-"} Ksh ${"%,.0f".format(tx.amount)} (${tx.source})"
                    textSize = 16f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(if (tx.category == "GAMBLING") "#DC2626" else if (isIn) "#059669" else "#0F172A"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                topRow.addView(TextView(this).apply {
                    text = tx.category
                    textSize = 11f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor(if (tx.category == "UNCLASSIFIED") "#B45309" else "#334155"))
                    background = makePill(if (tx.category == "UNCLASSIFIED") "#FEF3C7" else "#E2E8F0", 14f)
                    setPadding(18, 8, 18, 8)
                })
                card.addView(topRow)
                card.addView(TextView(this).apply {
                    text = "${tx.counterparty} • ${timeFmt.format(Date(tx.timestamp))}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    setPadding(0, 6, 0, 0)
                })
                txContainer.addView(card)
            }
        }
    }

    // --- DIALOGS ---
    private fun showBankTransactionDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 12) }
        val typeSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("BANK INCOME (+)", "BANK EXPENSE (-)"))
        }
        val amountBox = EditText(this).apply { hint = "Amount (Ksh)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val descBox = EditText(this).apply { hint = "Description (e.g. KCB Salary, Rent Transfer)" }
        layout.addView(typeSpinner)
        layout.addView(amountBox)
        layout.addView(descBox)

        AlertDialog.Builder(this)
            .setTitle("Log Bank / Cash Transaction")
            .setView(layout)
            .setPositiveButton("Save") { _, _ ->
                val amt = amountBox.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val desc = descBox.text.toString().trim().ifEmpty { "Bank Transfer" }
                val isIn = typeSpinner.selectedItemPosition == 0
                LocalFinanceStore.saveTransaction(
                    this,
                    MpesaTx(
                        id = "BANK_${System.currentTimeMillis()}",
                        direction = if (isIn) "IN" else "OUT",
                        amount = amt,
                        counterparty = desc,
                        category = if (isIn) "BANK SALARY/INCOME" else desc.uppercase(),
                        timestamp = System.currentTimeMillis(),
                        source = "BANK"
                    )
                )
                refreshDashboard()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddBillDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 12) }
        val nameBox = EditText(this).apply { hint = "Bill Name (e.g. Rent, KPLC, Water, Wi-Fi)" }
        val amountBox = EditText(this).apply { hint = "Monthly Amount (Ksh)"; inputType = InputType.TYPE_CLASS_NUMBER }
        val dayBox = EditText(this).apply { hint = "Due Day of Month (1 - 31)"; inputType = InputType.TYPE_CLASS_NUMBER }
        layout.addView(nameBox); layout.addView(amountBox); layout.addView(dayBox)

        AlertDialog.Builder(this)
            .setTitle("Add Monthly Fixed Bill")
            .setView(layout)
            .setPositiveButton("Add Bill") { _, _ ->
                val name = nameBox.text.toString().trim()
                val amt = amountBox.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                val day = (dayBox.text.toString().toIntOrNull() ?: 5).coerceIn(1, 31)
                if (name.isNotEmpty()) {
                    LocalFinanceStore.addMonthlyBill(this, MonthlyBill("BILL_${System.currentTimeMillis()}", name, amt, day))
                    refreshDashboard()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddDebtDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 24, 48, 12) }
        val lenderBox = EditText(this).apply { hint = "Lender (e.g. Fuliza, Tala, SACCO, Friend)" }
        val amountBox = EditText(this).apply { hint = "Amount Owed (Ksh)"; inputType = InputType.TYPE_CLASS_NUMBER }
        var selectedDueMillis = System.currentTimeMillis() + 7L * 86400_000L
        val dateBtn = Button(this).apply {
            text = "Pick Repayment Deadline"
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@MainActivity, { _, y, m, d ->
                    cal.set(y, m, d, 23, 59)
                    selectedDueMillis = cal.timeInMillis
                    text = "Deadline: $d/${m + 1}/$y"
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        layout.addView(lenderBox); layout.addView(amountBox); layout.addView(dateBtn)

        AlertDialog.Builder(this)
            .setTitle("Add Debt to Repayment Plan")
            .setView(layout)
            .setPositiveButton("Save Debt") { _, _ ->
                val lender = lenderBox.text.toString().trim()
                val amt = amountBox.text.toString().toDoubleOrNull() ?: return@setPositiveButton
                if (lender.isNotEmpty()) {
                    LocalFinanceStore.addDebt(this, DebtItem("DEBT_${System.currentTimeMillis()}", lender, amt, amt, selectedDueMillis))
                    refreshDashboard()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPaymentChannelDialog(onSelected: (String) -> Unit) {
        val channels = arrayOf("Paid via Bank App", "Paid via M-Pesa", "Paid via Cash")
        AlertDialog.Builder(this)
            .setTitle("How did you pay this?")
            .setItems(channels) { _, which -> onSelected(channels[which].removePrefix("Paid via ")) }
            .show()
    }

    private fun showPartnerPinSetupDialog() {
        if (LocalFinanceStore.hasPartnerPin(this) && !LocalFinanceStore.isTemporarilyUnlocked(this)) {
            startActivity(Intent(this, PartnerPinLockActivity::class.java))
            return
        }
        val input = EditText(this).apply {
            hint = "Partner creates 4-6 digit PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(40, 32, 40, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Accountability Partner Setup")
            .setMessage("Hand your phone to your accountability partner to set a PIN and lock Uninstall & Settings.")
            .setView(input)
            .setPositiveButton("Activate Shield") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length >= 4) {
                    LocalFinanceStore.setPartnerPin(this, pin)
                    if (!Settings.canDrawOverlays(this)) {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    } else {
                        launchDeviceAdminPrompt()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchDeviceAdminPrompt() {
        val adminComponent = ComponentName(this, SafePesaAdminReceiver::class.java)
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Prevents uninstalling SafePesa without partner PIN.")
        })
    }

    // --- UI HELPERS ---
    private fun createSectionHeader(title: String, actionText: String, onAction: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 32, 0, 12)
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#0F172A"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(this@MainActivity).apply {
                text = actionText
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#047857"))
                setPadding(16, 10, 16, 10)
                setOnClickListener { onAction() }
            })
        }
    }

    private fun emptyHint(msg: String): TextView {
        return TextView(this).apply {
            text = msg
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            background = makePill("#FFFFFF", 20f)
            setPadding(28, 28, 28, 28)
        }
    }

    private fun createStatCard(label: String, valueView: TextView, bgHex: String, fgHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makePill(bgHex, 20f)
            setPadding(20, 20, 20, 20)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = label; textSize = 9.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor(fgHex))
            })
            valueView.apply {
                text = "Ksh 0"; textSize = 13.5f; setTypeface(null, Typeface.BOLD); setTextColor(Color.parseColor(fgHex)); setPadding(0, 6, 0, 0)
            }
            addView(valueView)
        }
    }

    private fun createActionButton(label: String, bgHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label; textSize = 13f; setTypeface(null, Typeface.BOLD); setTextColor(Color.WHITE); gravity = Gravity.CENTER
            background = makePill(bgHex, 20f); setPadding(20, 24, 20, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun makePill(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply { setColor(Color.parseColor(hexColor)); cornerRadius = radius }
    }
}
