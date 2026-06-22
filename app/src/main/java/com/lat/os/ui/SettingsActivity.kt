
// com/lat/os/ui/SettingsActivity.kt
package com.lat.os.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var overlaySwitch: Switch
    private lateinit var accessibilitySwitch: Switch
    private lateinit var notificationSwitch: Switch
    private lateinit var microphoneSwitch: Switch
    private lateinit var apiKeyEdit: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF121212.toInt())
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val title = TextView(this).apply {
            text = "Permissions & Settings"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
        }
        root.addView(title)

        // 1. Overlay Permission
        root.addView(createPermissionRow("System Overlay", "Draw over other apps") { switch ->
            overlaySwitch = switch
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")))
        })

        // 2. Accessibility Service
        root.addView(createPermissionRow("Accessibility Service", "Screen reading & automation") { switch ->
            accessibilitySwitch = switch
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        // 3. Notification Listener
        root.addView(createPermissionRow("Notification Reader", "Read notifications aloud") { switch ->
            notificationSwitch = switch
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        // 4. Microphone
        root.addView(createPermissionRow("Microphone", "Voice commands") { switch ->
            microphoneSwitch = switch
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        })

        // API Key
        val apiLabel = TextView(this).apply {
            text = "Gemini API Key"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        }
        root.addView(apiLabel)
        apiKeyEdit = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("Enter API key")
            setHintTextColor(0xFF888888.toInt())
            val prefs = getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            setText(prefs.getString("gemini_api_key", ""))
        }
        root.addView(apiKeyEdit)

        val saveBtn = Button(this).apply {
            text = "Save Key"
            setBackgroundColor(0xFF0F9D58.toInt())
            setOnClickListener {
                getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
                    .edit().putString("gemini_api_key", apiKeyEdit.text.toString()).apply()
                Toast.makeText(this@SettingsActivity, "API Key saved", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(saveBtn)

        setContentView(root)
    }

    private fun createPermissionRow(title: String, subtitle: String, setupSwitch: (Switch) -> Unit): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 16)
        }
        val textCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textCol.addView(TextView(this).apply {
            text = title; setTextColor(0xFFFFFFFF.toInt()); textSize = 16f
        })
        textCol.addView(TextView(this).apply {
            text = subtitle; setTextColor(0xFFB0B0B0.toInt()); textSize = 12f
        })
        row.addView(textCol)
        val switch = Switch(this).apply {
            isClickable = false // status only, tap row to open settings
            isFocusable = false
        }
        row.addView(switch)
        setupSwitch(switch)
        row.setOnClickListener { setupSwitch(switch) }
        return row
    }

    private fun refreshPermissionStates() {
        overlaySwitch.isChecked = Settings.canDrawOverlays(this)
        accessibilitySwitch.isChecked = isAccessibilityServiceEnabled()
        notificationSwitch.isChecked = isNotificationListenerEnabled()
        microphoneSwitch.isChecked = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName + "/" + "com.lat.os.automation.VoiceAccessibilityService")
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
