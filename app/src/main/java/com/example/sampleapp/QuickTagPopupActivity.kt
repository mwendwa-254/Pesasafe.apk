package com.example.sampleapp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.sampleapp.data.LocalFinanceStore

class QuickTagPopupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val txId = intent.getStringExtra("TX_ID") ?: return finish()
        val direction = intent.getStringExtra("DIRECTION") ?: "OUT"
        val amount = intent.getDoubleExtra("AMOUNT", 0.0)
        val counterparty = intent.getStringExtra("COUNTERPARTY") ?: "M-Pesa"

        val isIn = direction == "IN"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(44, 40, 44, 40)
        }

        val badge = TextView(this).apply {
            text = if (isIn) "MONEY IN • M-PESA" else "MONEY OUT • M-PESA"
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            setTextColor(if (isIn) Color.parseColor("#047857") else Color.parseColor("#B91C1C"))
        }
        root.addView(badge)

        val title = TextView(this).apply {
            text = "${if (isIn) "+" else "-"} Ksh ${"%,.0f".format(amount)}"
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#0F172A"))
            setPadding(0, 6, 0, 4)
        }
        root.addView(title)

        val party = TextView(this).apply {
            text = "$counterparty\nTap to describe this transaction:"
            textSize = 13f
            setTextColor(Color.parseColor("#475569"))
            setPadding(0, 0, 0, 24)
        }
        root.addView(party)

        val categories = if (isIn) {
            listOf("SALARY", "DAILY HUSTLE", "BUSINESS", "LOAN RECEIVED", "GIFT/FAMILY")
        } else {
            listOf("FOOD", "RENT/BILLS", "TRANSPORT", "DEBT REPAYMENT", "AIRTIME/DATA", "IMPULSE/WANT")
        }

        // 2-column grid of 1-tap category buttons
        for (i in categories.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, 14)
            }
            for (j in 0..1) {
                if (i + j < categories.size) {
                    val cat = categories[i + j]
                    val btn = TextView(this).apply {
                        text = cat
                        textSize = 12f
                        setTypeface(null, Typeface.BOLD)
                        gravity = Gravity.CENTER
                        setTextColor(Color.parseColor("#064E3B"))
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#D1FAE5"))
                            cornerRadius = 20f
                        }
                        setPadding(20, 24, 20, 24)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (j == 0) rightMargin = 12
                        }
                        setOnClickListener {
                            LocalFinanceStore.updateCategory(this@QuickTagPopupActivity, txId, cat)
                            Toast.makeText(this@QuickTagPopupActivity, "Saved as $cat", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                    row.addView(btn)
                }
            }
            root.addView(row)
        }

        // Custom note input
        val customRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
        }
        val input = EditText(this).apply {
            hint = "Or custom description..."
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val saveBtn = Button(this).apply {
            text = "Save"
            setOnClickListener {
                val custom = input.text.toString().trim()
                if (custom.isNotEmpty()) {
                    LocalFinanceStore.updateCategory(this@QuickTagPopupActivity, txId, custom.uppercase())
                    finish()
                }
            }
        }
        customRow.addView(input)
        customRow.addView(saveBtn)
        root.addView(customRow)

        setContentView(root)
    }
}
