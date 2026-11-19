package com.example.sampleapp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.sampleapp.data.LocalFinanceStore

class PartnerPinLockActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Block hardware/gesture Back button from bypassing the lock
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitToHome()
            }
        })

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0F172A"))
            setPadding(64, 64, 64, 64)
        }

        val shieldIcon = TextView(this).apply {
            text = "🔒"
            textSize = 48f
            gravity = Gravity.CENTER
        }
        root.addView(shieldIcon)

        val title = TextView(this).apply {
            text = "Accountability Lock Active"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 12)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "SafePesa settings and uninstall controls are protected. Ask your accountability partner to enter their PIN to unlock for 60 seconds."
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }
        root.addView(subtitle)

        val pinInput = EditText(this).apply {
            hint = "Enter Partner PIN"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.BLACK)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
            }
            setPadding(32, 28, 32, 28)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        root.addView(pinInput)

        val unlockBtn = TextView(this).apply {
            text = "Unlock Settings (60s)"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#059669"))
                cornerRadius = 24f
            }
            setPadding(32, 32, 32, 32)
            setOnClickListener {
                val entered = pinInput.text.toString().trim()
                if (LocalFinanceStore.verifyAndTemporarilyUnlock(this@PartnerPinLockActivity, entered)) {
                    Toast.makeText(this@PartnerPinLockActivity, "Unlocked for 60 seconds", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    pinInput.error = "Incorrect Partner PIN"
                }
            }
        }
        val btnLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 }
        root.addView(unlockBtn, btnLp)

        val cancelBtn = TextView(this).apply {
            text = "Go Back to Safety"
            textSize = 14f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(24, 36, 24, 16)
            setOnClickListener { exitToHome() }
        }
        root.addView(cancelBtn)

        setContentView(root)
    }

    private fun exitToHome() {
        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(home)
        finish()
    }
}
