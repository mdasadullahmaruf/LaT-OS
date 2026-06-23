// app/src/main/java/com/lat/os/ui/SettingsActivity.kt
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
    private lateinit var picoKeyEdit: EditText
    private lateinit var appListText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionStates()
    }

    private fun buildUI() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0xFF121212.toInt())
        }

        // Title
        root.addView(TextView(this).apply {
            text = "Permissions & Settings"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // Permission rows
        root.addView(makePermRow("System Overlay",
            "Draw over other apps") { openOverlay() })
        root.addView(makePermRow("Accessibility Service",
            "Screen automation") { openAccessibility() })
        root.addView(makePermRow("Notification Reader",
            "Read notifications") { openNotifications() })
        root.addView(makePermRow("Microphone",
            "Voice commands") { requestMic() })

        // Divider
        root.addView(makeDivider())

        // Gemini API Key
        root.addView(TextView(this).apply {
            text = "Gemini API Key"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setPadding(0, 16, 0, 8)
        })
        apiKeyEdit = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("Enter Gemini API key")
            setHintTextColor(0xFF888888.toInt())
            setPadding(16, 16, 16, 16)
            val prefs = getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            setText(prefs.getString("gemini_api_key", ""))
        }
        root.addView(apiKeyEdit)
        root.addView(makeButton("Save Gemini Key", 0xFF0F9D58.toInt()) {
            getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("gemini_api_key", apiKeyEdit.text.toString().trim())
                .apply()
            Toast.makeText(this, "Gemini API Key saved!", Toast.LENGTH_SHORT).show()
        })

        // Divider
        root.addView(makeDivider())

        // App Scanner section
        root.addView(TextView(this).apply {
            text = "📱 App Scanner"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 16f
            setPadding(0, 16, 0, 4)
        })
        root.addView(TextView(this).apply {
            text = "Scan to see all installed apps and their exact package names. Share this list with the developer to fix app opening issues."
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })
        root.addView(makeButton("🔍 Scan My Installed Apps", 0xFF1565C0.toInt()) {
            scanInstalledApps()
        })

        appListText = TextView(this).apply {
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 11f
            setBackgroundColor(0xFF1E1E1E.toInt())
            setPadding(16, 16, 16, 16)
            text = "Tap scan button above to see your apps"
            setTextIsSelectable(true) // allows copy
        }
        root.addView(appListText)

        root.addView(makeButton("📋 Copy App List", 0xFF424242.toInt()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                    as android.content.ClipboardManager
            cm.setPrimaryClip(
                android.content.ClipData.newPlainText(
                    "App List", appListText.text))
            Toast.makeText(this, "Copied! Share with developer", 
                Toast.LENGTH_SHORT).show()
        })

        // Divider
        root.addView(makeDivider())

        // Voice test section
        root.addView(TextView(this).apply {
            text = "🧪 Test Voice Command"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 16f
            setPadding(0, 16, 0, 4)
        })
        val testInput = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("Type a command e.g. open youtube")
            setHintTextColor(0xFF888888.toInt())
            setPadding(16, 16, 16, 16)
        }
        root.addView(testInput)
        root.addView(makeButton("▶ Test This Command", 0xFF6A1B9A.toInt()) {
            val cmd = testInput.text.toString().trim()
            if (cmd.isNotBlank()) {
                com.lat.os.engine.DecisionRouter.init(this)
                com.lat.os.engine.DecisionRouter.route(this, cmd)
                Toast.makeText(this, "Testing: $cmd", Toast.LENGTH_SHORT).show()
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun scanInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val apps = pm.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(pm).toString().lowercase() }

        val sb = StringBuilder()
        sb.appendLine("=== INSTALLED APPS (${apps.size}) ===\n")
        for (app in apps) {
            val name = app.loadLabel(pm).toString()
            val pkg = app.activityInfo.packageName
            sb.appendLine("$name")
            sb.appendLine("  → $pkg")
            sb.appendLine()
        }
        appListText.text = sb.toString()
        Toast.makeText(this,
            "${apps.size} apps found! Copy and share list.",
            Toast.LENGTH_LONG).show()
    }

    // ── PERMISSION HELPERS ───────────────────────────────────────

    private fun makePermRow(title: String, subtitle: String,
                            onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 20, 0, 20)
            setOnClickListener { onClick() }
        }
        val texts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        texts.addView(TextView(this).apply {
            text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        })
        texts.addView(TextView(this).apply {
            text = subtitle
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
        })
        row.addView(texts)

        val sw = Switch(this).apply {
            isClickable = false
            isFocusable = false
        }
        row.addView(sw)

        // Store reference
        when (title) {
            "System Overlay" -> overlaySwitch = sw
            "Accessibility Service" -> accessibilitySwitch = sw
            "Notification Reader" -> notificationSwitch = sw
            "Microphone" -> microphoneSwitch = sw
        }
        return row
    }

    private fun makeButton(label: String, color: Int,
                           onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            setBackgroundColor(color)
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(16, 24, 16, 24)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
            setOnClickListener { onClick() }
        }
    }

    private fun makeDivider(): TextView {
        return TextView(this).apply {
            setBackgroundColor(0xFF333333.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 24, 0, 24) }
        }
    }

    private fun refreshPermissionStates() {
        if (::overlaySwitch.isInitialized)
            overlaySwitch.isChecked = Settings.canDrawOverlays(this)
        if (::accessibilitySwitch.isInitialized)
            accessibilitySwitch.isChecked = isAccessibilityOn()
        if (::notificationSwitch.isInitialized)
            notificationSwitch.isChecked = isNotificationListenerOn()
        if (::microphoneSwitch.isInitialized)
            microphoneSwitch.isChecked = ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun openOverlay() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")))
    }

    private fun openAccessibility() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun openNotifications() {
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun requestMic() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
    }

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.contains(
            "$packageName/com.lat.os.automation.VoiceAccessibilityService")
    }

    private fun isNotificationListenerOn(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
