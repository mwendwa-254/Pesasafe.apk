package com.example.sampleapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.sampleapp.data.LocalFinanceStore

class MainActivity : AppCompatActivity() {

    private lateinit var lossText: TextView
    private lateinit var adviceText: TextView
    private lateinit var txContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 48, 40, 48)
        }
        scroll.addView(root)

        val title = TextView(this).apply {
            text = "SafePesa Recovery (100% Offline)"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
        }
        root.addView(title)

        // Setup Buttons
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 24, 0, 24)
        }
        val btnMpesa = Button(this).apply {
            text = "1. Enable M-Pesa Reader"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        }
        val btnBlocker = Button(this).apply {
            text = "2. Enable Game Blocker"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        btnRow.addView(btnMpesa)
        btnRow.addView(btnBlocker)
        root.addView(btnRow)

        // Gambling Loss Banner
        lossText = TextView(this).apply {
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#B71C1C"))
            setBackgroundColor(Color.parseColor("#FFEBEE"))
            setPadding(32, 32, 32, 32)
        }
        root.addView(lossText)

        // Financial Advisor Box
        adviceText = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#1B5E20"))
            setBackgroundColor(Color.parseColor("#E8F5E9"))
            setPadding(32, 32, 32, 32)
        }
        val adviceParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24; bottomMargin = 32 }
        root.addView(adviceText, adviceParams)

        // Ledger Header + Refresh Button
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val ledgerTitle = TextView(this).apply {
            text = "M-Pesa Ledger (Describe Transactions)"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val refreshBtn = Button(this).apply {
            text = "Refresh"
            setOnClickListener { refreshDashboard() }
        }
        headerRow.addView(ledgerTitle)
        headerRow.addView(refreshBtn)
        root.addView(headerRow)

        txContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 0)
        }
        root.addView(txContainer)

        setContentView(scroll)
        refreshDashboard()
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        val totalLost = LocalFinanceStore.getTotalGamblingLoss(this)
        lossText.text = "Total Tracked Betting Losses:\nKsh ${"%,.2f".format(totalLost)}"
        adviceText.text = "Financial Advisor:\n${LocalFinanceStore.generateFinancialAdvice(this)}"

        txContainer.removeAllViews()
        val txs = LocalFinanceStore.getAllTransactions(this)

        if (txs.isEmpty()) {
            txContainer.addView(TextView(this).apply {
                text = "No M-Pesa messages captured yet. Once enabled, incoming M-Pesa notifications will appear here."
                setPadding(0, 24, 0, 0)
            })
            return
        }

        for (tx in txs) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#F5F5F5"))
                setPadding(24, 24, 24, 24)
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 20 }

            val topLine = TextView(this).apply {
                text = "${tx.direction}: Ksh ${"%,.0f".format(tx.amount)}  •  [${tx.category}]"
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (tx.category == "GAMBLING") Color.RED else Color.BLACK)
            }
            val subLine = TextView(this).apply {
                text = tx.counterparty
            }
            card.addView(topLine)
            card.addView(subLine)

            if (tx.category == "UNCLASSIFIED") {
                val inputRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 12, 0, 0)
                }
                val editBox = EditText(this).apply {
                    hint = "What was this for? (e.g. Lunch, Rent)"
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                val saveBtn = Button(this).apply {
                    text = "Save"
                    setOnClickListener {
                        val note = editBox.text.toString().trim()
                        if (note.isNotEmpty()) {
                            LocalFinanceStore.updateCategory(this@MainActivity, tx.id, note.uppercase())
                            refreshDashboard()
                        }
                    }
                }
                inputRow.addView(editBox)
                inputRow.addView(saveBtn)
                card.addView(inputRow)
            }

            txContainer.addView(card, cardParams)
        }
    }
}
