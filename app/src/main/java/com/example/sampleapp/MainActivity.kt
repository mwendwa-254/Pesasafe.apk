package com.example.sampleapp

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.example.sampleapp.service.SafePesaAdminReceiver
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.sampleapp.data.LocalFinanceStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var netBalanceText: TextView
    private lateinit var healthBadgeText: TextView
    private lateinit var moneyInText: TextView
    private lateinit var moneyOutText: TextView
    private lateinit var gamblingLossText: TextView
    private lateinit var adviceText: TextView
    private lateinit var txContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = Color.parseColor("#064E3B")

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F1F5F9"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(root)

        // 1. TOP FINTECH HEADER (Deep Emerald)
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#064E3B"))
            setPadding(48, 56, 48, 64)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val appTitle = TextView(this).apply {
            text = "SafePesa"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val offlineBadge = TextView(this).apply {
            text = "100% ON-DEVICE"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#A7F3D0"))
            background = makePill("#047857", 24f)
            setPadding(24, 10, 24, 10)
        }
        topBar.addView(appTitle)
        topBar.addView(offlineBadge)
        header.addView(topBar)

        val netLabel = TextView(this).apply {
            text = "Tracked Net Cash Flow"
            textSize = 13f
            setTextColor(Color.parseColor("#A7F3D0"))
            setPadding(0, 32, 0, 4)
        }
        header.addView(netLabel)

        netBalanceText = TextView(this).apply {
            text = "Ksh 0.00"
            textSize = 32f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        header.addView(netBalanceText)

        healthBadgeText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#ECFDF5"))
            setPadding(0, 12, 0, 0)
        }
        header.addView(healthBadgeText)
        root.addView(header)

        // MAIN CONTENT BODY
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 32, 36, 48)
        }
        root.addView(body)

        // 2. PROTECTION SETUP PILLS
        val permRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnMpesa = createActionButton("M-Pesa Reader", "#0F766E") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        val btnBlocker = createActionButton("Crash Blocker", "#1E293B") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val btnSpacer = View(this).apply { layoutParams = LinearLayout.LayoutParams(20, 1) }
        permRow.addView(btnMpesa)
        permRow.addView(btnSpacer)
        permRow.addView(btnBlocker)
        body.addView(permRow)
        val btnPartnerLock = createActionButton(
            if (LocalFinanceStore.hasPartnerPin(this)) "🔒 Accountability Shield Active" else "Set Accountability Partner PIN",
            "#B45309"
        ) {
            showPartnerPinSetupDialog()
        }
        val lockBtnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 16 }
        body.addView(btnPartnerLock, lockBtnLp)

        // 3. THREE METRIC CARDS (Money In | Living Out | Betting Loss)
        val metricsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 28, 0, 24)
        }

        moneyInText = TextView(this)
        moneyOutText = TextView(this)
        gamblingLossText = TextView(this)

        metricsRow.addView(createStatCard("MONEY IN", moneyInText, "#ECFDF5", "#047857"))
        metricsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) })
        metricsRow.addView(createStatCard("EXPENSES", moneyOutText, "#EFF6FF", "#1D4ED8"))
        metricsRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(16, 1) })
        metricsRow.addView(createStatCard("BETTING LOSS", gamblingLossText, "#FEF2F2", "#B91C1C"))
        body.addView(metricsRow)

        // 4. FINANCIAL ADVISOR CARD
        val advisorCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makePill("#FFFFFF", 28f)
            setPadding(36, 32, 36, 32)
            elevation = 4f
        }
        val advisorHeader = TextView(this).apply {
            text = "Smart Financial Advisor"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#064E3B"))
        }
        adviceText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.parseColor("#334155"))
            setPadding(0, 12, 0, 0)
            setLineSpacing(6f, 1f)
        }
        advisorCard.addView(advisorHeader)
        advisorCard.addView(adviceText)
        body.addView(advisorCard)

        // 5. LEDGER SECTION HEADER
        val ledgerHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 40, 0, 16)
        }
        val ledgerTitle = TextView(this).apply {
            text = "M-Pesa Cash Flow Ledger"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val refreshBtn = TextView(this).apply {
            text = "↻ Refresh"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#047857"))
            setPadding(20, 12, 20, 12)
            setOnClickListener { refreshDashboard() }
        }
        ledgerHeader.addView(ledgerTitle)
        ledgerHeader.addView(refreshBtn)
        body.addView(ledgerHeader)

        txContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        body.addView(txContainer)

        setContentView(scroll)
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val txs = LocalFinanceStore.getAllTransactions(this)
        val totalIn = txs.filter { it.direction == "IN" }.sumOf { it.amount }
        val totalLost = LocalFinanceStore.getTotalGamblingLoss(this)
        val livingOut = txs.filter { it.direction == "OUT" && it.category != "GAMBLING" }.sumOf { it.amount }
        val net = totalIn - (livingOut + totalLost)

        netBalanceText.text = "Ksh ${"%,.2f".format(net)}"
        moneyInText.text = "Ksh ${"%,.0f".format(totalIn)}"
        moneyOutText.text = "Ksh ${"%,.0f".format(livingOut)}"
        gamblingLossText.text = "Ksh ${"%,.0f".format(totalLost)}"

        healthBadgeText.text = when {
            totalLost > 0 -> "⚠ Ksh ${"%,.0f".format(totalLost)} diverted to betting — blocker active"
            totalIn > 0 -> "✓ 0% lost to gambling — keep building your surplus"
            else -> "Ready to track M-Pesa & block crash games"
        }

        adviceText.text = LocalFinanceStore.generateFinancialAdvice(this)

        txContainer.removeAllViews()
        if (txs.isEmpty()) {
            val emptyCard = TextView(this).apply {
                text = "No M-Pesa transactions recorded yet.\nEnable M-Pesa Reader above—new incoming and outgoing M-Pesa notifications will appear here automatically."
                textSize = 14f
                setTextColor(Color.parseColor("#64748B"))
                background = makePill("#FFFFFF", 24f)
                setPadding(36, 40, 36, 40)
            }
            txContainer.addView(emptyCard)
            return
        }

        val timeFmt = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())

        for (tx in txs) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = makePill("#FFFFFF", 24f)
                setPadding(32, 28, 32, 28)
                elevation = 2f
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }

            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val isIn = tx.direction == "IN"
            val amountColor = when {
                tx.category == "GAMBLING" -> "#DC2626"
                isIn -> "#059669"
                else -> "#0F172A"
            }
            val sign = if (isIn) "+" else "-"

            val amountView = TextView(this).apply {
                text = "$sign Ksh ${"%,.0f".format(tx.amount)}"
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(amountColor))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val badgeBg = if (tx.category == "GAMBLING") "#FEE2E2" else if (tx.category == "UNCLASSIFIED") "#FEF3C7" else "#E2E8F0"
            val badgeFg = if (tx.category == "GAMBLING") "#B91C1C" else if (tx.category == "UNCLASSIFIED") "#B45309" else "#334155"

            val tagBadge = TextView(this).apply {
                text = tx.category
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(badgeFg))
                background = makePill(badgeBg, 16f)
                setPadding(20, 8, 20, 8)
            }
            topRow.addView(amountView)
            topRow.addView(tagBadge)
            card.addView(topRow)

            val partyView = TextView(this).apply {
                text = "${tx.counterparty}  •  ${timeFmt.format(Date(tx.timestamp))}"
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                setPadding(0, 8, 0, 0)
            }
            card.addView(partyView)

            // Interactive Quick-Tag Chips + Custom Input for Unclassified Transactions
            if (tx.category == "UNCLASSIFIED") {
                val promptLabel = TextView(this).apply {
                    text = "Describe this transaction:"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#475569"))
                    setPadding(0, 20, 0, 10)
                }
                card.addView(promptLabel)

                val chips = if (isIn) listOf("SALARY", "HUSTLE", "BUSINESS", "LOAN/GIFT")
                else listOf("FOOD", "RENT/BILLS", "TRANSPORT", "FAMILY")

                val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chips.forEach { label ->
                    val chip = TextView(this).apply {
                        text = label
                        textSize = 11f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(Color.parseColor("#064E3B"))
                        background = makePill("#D1FAE5", 16f)
                        setPadding(18, 12, 18, 12)
                        setOnClickListener {
                            LocalFinanceStore.updateCategory(this@MainActivity, tx.id, label)
                            refreshDashboard()
                        }
                    }
                    val lp = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { rightMargin = 12 }
                    chipRow.addView(chip, lp)
                }
                card.addView(chipRow)

                val customRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 16, 0, 0)
                    gravity = Gravity.CENTER_VERTICAL
                }
                val editBox = EditText(this).apply {
                    hint = "Or type custom note..."
                    textSize = 13f
                    background = makePill("#F1F5F9", 16f)
                    setPadding(24, 18, 24, 18)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val saveBtn = TextView(this).apply {
                    text = "Save"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = makePill("#064E3B", 16f)
                    setPadding(32, 18, 32, 18)
                    setOnClickListener {
                        val note = editBox.text.toString().trim()
                        if (note.isNotEmpty()) {
                            LocalFinanceStore.updateCategory(this@MainActivity, tx.id, note.uppercase())
                            refreshDashboard()
                        }
                    }
                }
                val saveLp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { leftMargin = 12 }
                customRow.addView(editBox)
                customRow.addView(saveBtn, saveLp)
                card.addView(customRow)
            }

            txContainer.addView(card, cardParams)
        }
    }

    private fun createStatCard(label: String, valueView: TextView, bgHex: String, fgHex: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makePill(bgHex, 24f)
            setPadding(24, 24, 24, 24)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 10f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(fgHex))
            })
            valueView.apply {
                text = "Ksh 0"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor(fgHex))
                setPadding(0, 8, 0, 0)
            }
            addView(valueView)
        }
    }

    private fun createActionButton(label: String, bgHex: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = makePill(bgHex, 24f)
            setPadding(24, 28, 24, 28)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { onClick() }
        }
    }

    private fun makePill(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hexColor))
            cornerRadius = radius
        }
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
            .setMessage("Hand your phone to your accountability partner. Once they set a PIN and activate Uninstall Protection, SafePesa cannot be uninstalled or disabled without their PIN.")
            .setView(input)
            .setPositiveButton("Activate Shield") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length >= 4) {
                    LocalFinanceStore.setPartnerPin(this, pin)
                    val adminComponent = ComponentName(this, SafePesaAdminReceiver::class.java)
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                        putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "Prevents uninstalling SafePesa without your accountability partner's PIN."
                        )
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
