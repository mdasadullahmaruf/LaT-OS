
// com/lat/os/core/BootReceiver.kt
package com.lat.os.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("master_enabled", false)) {
                val serviceIntent = Intent(context, WakeWordService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
