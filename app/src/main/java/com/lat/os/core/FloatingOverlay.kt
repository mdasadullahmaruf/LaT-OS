// app/src/main/java/com/lat/os/core/FloatingOverlay.kt
package com.lat.os.core

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FloatingOverlay : Service() {

    companion object {
        const val CHANNEL_ID = "overlay_channel"
        const val NOTIF_ID = 2
        var instance: FloatingOverlay? = null
    }

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var wakeWordService: WakeWordService? = null
    private lateinit var params: WindowManager.LayoutParams
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    inner class OverlayBinder : Binder() {
        fun updateState(active: Boolean) {
            overlayView?.post {
                overlayView?.setBackgroundColor(
                    if (active) 0xFF0F9D58.toInt() else 0xFFD32F2F.toInt()
                )
            }
        }
    }

    private val binder = OverlayBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val wakeWordConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wakeWordService = (service as WakeWordService.LocalBinder).getService()
            wakeWordService?.setOverlayBinder(binder)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            wakeWordService = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        // MUST call startForeground before addView
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayButton()
        // Bind to WakeWordService
        try {
            bindService(
                Intent(this, WakeWordService::class.java),
                wakeWordConnection,
                Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createOverlayButton() {
        params = WindowManager.LayoutParams(
            150, 150,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 300
        }

        overlayView = TextView(this).apply {
            text = "✦"
            textSize = 26f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFD32F2F.toInt())
            elevation = 10f

            setOnTouchListener(object : View.OnTouchListener {
                private var hasMoved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            hasMoved = false
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 8 || Math.abs(dy) > 8) hasMoved = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            try {
                                windowManager?.updateViewLayout(overlayView, params)
                            } catch (e: Exception) { }
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!hasMoved) {
                                // Single tap = start listening
                                setBackgroundColor(0xFF0F9D58.toInt())
                                wakeWordService?.startCommandListening()
                                    ?: run {
                                        // WakeWordService not bound yet, try direct
                                        postDelayed({
                                            setBackgroundColor(0xFFD32F2F.toInt())
                                        }, 4000)
                                    }
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        try {
            windowManager?.addView(overlayView, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildNotification(): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaT OS Active")
            .setContentText("Floating button is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "LaT OS floating button"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) { }
        try { unbindService(wakeWordConnection) } catch (e: Exception) { }
        super.onDestroy()
    }
}
