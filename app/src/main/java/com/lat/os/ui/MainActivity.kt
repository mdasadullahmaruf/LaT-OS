
// com/lat/os/ui/MainActivity.kt
package com.lat.os.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lat.os.core.WakeWordService

class MainActivity : AppCompatActivity() {

    private lateinit var masterSwitch: Switch
    private lateinit var statusText: TextView
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
        buildUI()
        updateUI()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF121212.toInt())
        }

        val title = TextView(this).apply {
            text = "LaT OS"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Hands‑Free Voice Control"
            textSize = 16f
            setTextColor(0xFFB0B0B0.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(subtitle)

        // Master Switch
        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 32, 0, 0)
        }
        val switchLabel = TextView(this).apply {
            text = "Enable Assistant:"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
        }
        switchRow.addView(switchLabel)
        masterSwitch = Switch(this).apply {
            setOnCheckedChangeListener { _, isChecked ->
                prefs.edit().putBoolean("master_enabled", isChecked).apply()
                updateServiceState(isChecked)
                updateUI()
            }
        }
        switchRow.addView(masterSwitch)

        root.addView(switchRow)

        statusText = TextView(this).apply {
            setPadding(0, 16, 0, 32)
            setTextColor(0xFFB0B0B0.toInt())
        }
        root.addView(statusText)

        // Buttons
        val btnSettings = Button(this).apply {
            text = "Permissions & Settings"
            setBackgroundColor(0xFF2C2C2C.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        root.addView(btnSettings)

        val btnHistory = Button(this).apply {
            text = "Conversation History"
            setBackgroundColor(0xFF2C2C2C.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
            }
        }
        root.addView(btnHistory)

        setContentView(root)
    }

    private fun updateUI() {
        val enabled = prefs.getBoolean("master_enabled", false)
        masterSwitch.isChecked = enabled
        statusText.text = if (enabled) "🟢 Assistant active" else "🔴 Assistant stopped"
    }

    private fun updateServiceState(enable: Boolean) {
        val intent = Intent(this, WakeWordService::class.java)
        if (enable) {
            ContextCompat.startForegroundService(this, intent)
        } else {
            stopService(intent)
        }
    }
}
