// app/src/main/java/com/lat/os/ui/SettingsActivity.kt
package com.lat.os.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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

        root.addView(TextView(this).apply {
            text = "Permissions & Settings"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        })

        // ── PERMISSIONS ──────────────────────────────────────────
        root.addView(makeSectionLabel("🔐 Required Permissions"))

        root.addView(makePermRow(
            "System Overlay",
            "Draw floating button over apps"
        ) {
            startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        })

        root.addView(makePermRow(
            "Accessibility Service",
            "Screen reading and automation"
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        root.addView(makePermRow(
            "Notification Reader",
            "Read notifications aloud"
        ) {
            startActivity(Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })

        root.addView(makePermRow(
            "Microphone",
            "Voice commands and wake word"
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                100
            )
        })

        root.addView(makeDivider())

        // ── GEMINI API KEY ───────────────────────────────────────
        root.addView(makeSectionLabel("🤖 Gemini API Key"))

        root.addView(TextView(this).apply {
            text = "Get free key from aistudio.google.com"
            setTextColor(0xFF4CAF50.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })

        apiKeyEdit = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("Paste your Gemini API key here")
            setHintTextColor(0xFF666666.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 13f
            val prefs = getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            setText(prefs.getString("gemini_api_key", ""))
        }
        root.addView(apiKeyEdit)

        root.addView(makeButton("💾 Save Gemini Key", 0xFF0F9D58.toInt()) {
            val key = apiKeyEdit.text.toString().trim()
            getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("gemini_api_key", key)
                .apply()
            if (key.isNotBlank()) {
                Toast.makeText(this, "✅ Gemini API Key saved!",
                    Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "⚠ Key is empty",
                    Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(makeDivider())

        // ── APP SCANNER ──────────────────────────────────────────
        root.addView(makeSectionLabel("📱 App Scanner"))

        root.addView(TextView(this).apply {
            text = "Scans ALL installed apps including YouTube, WhatsApp etc."
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 12)
        })

        root.addView(makeButton("🔍 Scan All Installed Apps", 0xFF1565C0.toInt()) {
            scanInstalledApps()
        })

        appListText = TextView(this).apply {
            setTextColor(0xFFE0E0E0.toInt())
            textSize = 11f
            setBackgroundColor(0xFF1A1A1A.toInt())
            setPadding(20, 20, 20, 20)
            text = "Tap scan button to see all your apps..."
            setTextIsSelectable(true)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 8) }
        }
        root.addView(appListText)

        root.addView(makeButton("📋 Copy App List", 0xFF424242.toInt()) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            cm.setPrimaryClip(
                ClipData.newPlainText("App List", appListText.text)
            )
            Toast.makeText(this,
                "✅ Copied! Paste it in chat to fix app issues.",
                Toast.LENGTH_LONG).show()
        })

        root.addView(makeDivider())

        // ── VOICE COMMAND TESTER ─────────────────────────────────
        root.addView(makeSectionLabel("🧪 Test Voice Command"))

        root.addView(TextView(this).apply {
            text = "Type any command to test without speaking"
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })

        val testInput = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("e.g. open youtube")
            setHintTextColor(0xFF666666.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 14f
        }
        root.addView(testInput)

        root.addView(makeButton("▶ Run Test Command", 0xFF6A1B9A.toInt()) {
            val cmd = testInput.text.toString().trim()
            if (cmd.isNotBlank()) {
                com.lat.os.engine.DecisionRouter.init(this)
                com.lat.os.engine.DecisionRouter.route(this, cmd)
                Toast.makeText(this,
                    "Running: $cmd", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this,
                    "Please type a command first",
                    Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(makeDivider())

        // ── APP FINDER TESTER ────────────────────────────────────
        root.addView(makeSectionLabel("🔎 Test App Finder"))

        root.addView(TextView(this).apply {
            text = "Test if the app can find a specific app by name"
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
            setPadding(0, 0, 0, 8)
        })

        val appTestInput = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF2C2C2C.toInt())
            setHint("e.g. youtube")
            setHintTextColor(0xFF666666.toInt())
            setPadding(24, 20, 24, 20)
            textSize = 14f
        }
        root.addView(appTestInput)

        val appTestResult = TextView(this).apply {
            setTextColor(0xFF4CAF50.toInt())
            textSize = 13f
            setPadding(0, 8, 0, 8)
            text = ""
        }
        root.addView(appTestResult)

        root.addView(makeButton("🔍 Find This App", 0xFF00695C.toInt()) {
            val query = appTestInput.text.toString().trim()
            if (query.isNotBlank()) {
                val pkg = com.lat.os.engine.PackageMapper
                    .findPackage(this, query)
                if (pkg != null) {
                    appTestResult.setTextColor(0xFF4CAF50.toInt())
                    appTestResult.text = "✅ Found: $pkg"
                } else {
                    appTestResult.setTextColor(0xFFFF5252.toInt())
                    appTestResult.text = "❌ Not found: '$query' not installed"
                }
            }
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    // ── APP SCANNER ──────────────────────────────────────────────

    private fun scanInstalledApps() {
        val pm = packageManager
        val results = mutableListOf<Pair<String, String>>()

        try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
                .forEach { appInfo ->
                    try {
                        val pkg = appInfo.packageName
                        if (pm.getLaunchIntentForPackage(pkg) != null) {
                            val label = pm.getApplicationLabel(appInfo)
                                .toString()
                            results.add(Pair(label, pkg))
                        }
                    } catch (e: Exception) { }
                }
        } catch (e: Exception) { }

        try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            pm.queryIntentActivities(intent, 0).forEach { info ->
                val label = info.loadLabel(pm).toString()
                val pkg = info.activityInfo.packageName
                if (results.none { it.second == pkg }) {
                    results.add(Pair(label, pkg))
                }
            }
        } catch (e: Exception) { }

        val sorted = results.sortedBy { it.first.lowercase() }

        val sb = StringBuilder()
        sb.appendLine("=== ALL APPS (${sorted.size}) ===\n")
        for ((name, pkg) in sorted) {
            sb.appendLine("$name → $pkg")
        }
        appListText.text = sb.toString()

        Toast.makeText(this,
            "✅ Found ${sorted.size} apps!",
            Toast.LENGTH_LONG).show()
    }

    // ── UI HELPERS ───────────────────────────────────────────────

    private fun makeSectionLabel(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 16f
            setTextColor(0xFF4CAF50.toInt())
            setPadding(0, 8, 0, 12)
        }
    }

    private fun makePermRow(
        title: String,
        subtitle: String,
        onClick: () -> Unit
    ): LinearLayout {
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
            this.text = title
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
        })
        texts.addView(TextView(this).apply {
            this.text = subtitle
            setTextColor(0xFFB0B0B0.toInt())
            textSize = 12f
        })
        row.addView(texts)

        val sw = Switch(this).apply {
            isClickable = false
            isFocusable = false
        }
        row.addView(sw)

        when (title) {
            "System Overlay" -> overlaySwitch = sw
            "Accessibility Service" -> accessibilitySwitch = sw
            "Notification Reader" -> notificationSwitch = sw
            "Microphone" -> microphoneSwitch = sw
        }

        return row
    }

    private fun makeButton(
        label: String,
        color: Int,
        onClick: () -> Unit
    ): Button {
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

    private fun makeDivider(): View {
        return View(this).apply {
            setBackgroundColor(0xFF2A2A2A.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).apply { setMargins(0, 24, 0, 24) }
        }
    }

    // ── PERMISSION CHECKS ────────────────────────────────────────

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

    private fun isAccessibilityOn(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(
            "$packageName/com.lat.os.automation.VoiceAccessibilityService"
        )
    }

    private fun isNotificationListenerOn(): Boolean {
        val flat = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )
        return flat?.contains(packageName) == true
    }
}
