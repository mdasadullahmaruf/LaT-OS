// app/src/main/java/com/lat/os/ui/MainActivity.kt
package com.lat.os.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.lat.os.core.FloatingOverlay
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

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun buildUI() {
        val scroll = android.widget.ScrollView(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF121212.toInt())
        }

        val title = TextView(this).apply {
            text = "LaT OS"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8)
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Hands-Free Voice Control"
            textSize = 16f
            setTextColor(0xFFB0B0B0.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 48)
        }
        root.addView(subtitle)

        // Status card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1E1E1E.toInt())
            setPadding(32, 32, 32, 32)
        }

        val switchRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val switchLabel = TextView(this).apply {
            text = "Enable Assistant"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 18f
            layoutParams = LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        switchRow.addView(switchLabel)

        masterSwitch = Switch(this).apply {
            isChecked = prefs.getBoolean("master_enabled", false)
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    // Check overlay permission first
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                        !Settings.canDrawOverlays(this@MainActivity)) {
                        Toast.makeText(this@MainActivity,
                            "Please grant Overlay permission first!", Toast.LENGTH_LONG).show()
                        masterSwitch.isChecked = false
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                        return@setOnCheckedChangeListener
                    }
                }
                prefs.edit().putBoolean("master_enabled", isChecked).apply()
                updateServiceState(isChecked)
                updateUI()
            }
        }
        switchRow.addView(masterSwitch)
        card.addView(switchRow)

        statusText = TextView(this).apply {
            setPadding(0, 16, 0, 0)
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 14f
        }
        card.addView(statusText)
        root.addView(card)

        // Spacer
        root.addView(TextView(this).apply { setPadding(0, 24, 0, 0) })

        // Wake word info box
        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A2A1A.toInt())
            setPadding(32, 24, 32, 24)
        }
        infoBox.addView(TextView(this).apply {
            text = "🎤 Wake Word"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 14f
        })
        infoBox.addView(TextView(this).apply {
            text = "Say \"Hey Phone\" to activate"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setPadding(0, 8, 0, 0)
        })
        infoBox.addView(TextView(this).apply {
            text = "Or tap the ✦ floating button"
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 13f
            setPadding(0, 4, 0, 0)
        })
        root.addView(infoBox)

        root.addView(TextView(this).apply { setPadding(0, 24, 0, 0) })

        // Buttons
        val btnSettings = Button(this).apply {
            text = "⚙ Permissions & Settings"
            setBackgroundColor(0xFF2C2C2C.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
            setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
        root.addView(btnSettings)

        val btnHistory = Button(this).apply {
            text = "📋 Conversation History"
            setBackgroundColor(0xFF2C2C2C.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(32, 24, 32, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HistoryActivity::class.java))
            }
        }
        root.addView(btnHistory)

        // Model warning
        root.addView(TextView(this).apply {
            text = "⚠ Note: Vosk model required for wake word.\nPlace 'vosk-model-small-en-us-0.15' in app's external files folder."
            setTextColor(0xFFFF9800.toInt())
            textSize = 12f
            setPadding(0, 32, 0, 0)
            gravity = Gravity.CENTER
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun updateUI() {
        val enabled = prefs.getBoolean("master_enabled", false)
        if (::masterSwitch.isInitialized) masterSwitch.isChecked = enabled
        if (::statusText.isInitialized) {
            statusText.text = if (enabled)
                "🟢 Assistant active — floating button visible"
            else
                "🔴 Assistant stopped"
        }
    }

    private fun updateServiceState(enable: Boolean) {
        val wakeIntent = Intent(this, WakeWordService::class.java)
        val overlayIntent = Intent(this, FloatingOverlay::class.java)
        if (enable) {
            ContextCompat.startForegroundService(this, wakeIntent)
            ContextCompat.startForegroundService(this, overlayIntent)
        } else {
            stopService(wakeIntent)
            stopService(overlayIntent)
        }
    }
}
