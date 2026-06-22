// com/lat/os/core/FloatingOverlay.kt
package com.lat.os.core

import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

class FloatingOverlay : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isActive = false
    private var wakeWordService: WakeWordService? = null

    inner class OverlayBinder : Binder() {
        fun updateState(active: Boolean) {
            this@FloatingOverlay.isActive = active
            overlayView?.post { overlayView?.setBackgroundColor(if (active) 0xFF0F9D58.toInt() else 0xFFD32F2F.toInt()) }
        }
    }
    private val binder = OverlayBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bindWakeWordService()
        createOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun bindWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                wakeWordService = (service as WakeWordService.LocalBinder).getService()
                wakeWordService?.setOverlayBinder(binder)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                wakeWordService?.setOverlayBinder(null)
                wakeWordService = null
            }
        }, Context.BIND_AUTO_CREATE)
    }

    private fun createOverlayView() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        val view = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xFFD32F2F.toInt()) // red idle
            val circle = ImageView(this@FloatingOverlay).apply {
                setImageResource(0) // we draw programmatically
                // just a text symbol
            }
            val symbol = android.widget.TextView(this@FloatingOverlay).apply {
                text = "✦"
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 24f
                gravity = Gravity.CENTER
                setPadding(16, 16, 16, 16)
            }
            addView(symbol)
            setOnClickListener {
                wakeWordService?.startCommandListening()
            }
        }

        windowManager?.addView(view, params)
        overlayView = view
    }

    override fun onDestroy() {
        if (overlayView != null) windowManager?.removeView(overlayView)
        super.onDestroy()
    }
}
