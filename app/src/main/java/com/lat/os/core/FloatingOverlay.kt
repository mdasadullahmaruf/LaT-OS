// app/src/main/java/com/lat/os/core/FloatingOverlay.kt
package com.lat.os.core

import android.app.*
import android.content.*
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
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isActive = false
    private var wakeWordService: WakeWordService? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private lateinit var params: WindowManager.LayoutParams

    inner class OverlayBinder : Binder() {
        fun updateState(active: Boolean) {
            isActive = active
            overlayView?.post {
                (overlayView as? TextView)?.setBackgroundColor(
                    if (active) 0xFF0F9D58.toInt() else 0xFFD32F2F.toInt()
                )
            }
        }
    }

    private val binder = OverlayBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createOverlayButton()
        bindToWakeWordService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createOverlayButton() {
        params = WindowManager.LayoutParams(
            160, 160,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        val button = TextView(this).apply {
            text = "✦"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFD32F2F.toInt()) // red = idle

            setOnTouchListener(object : View.OnTouchListener {
                var moved = false

                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            moved = false
                            initialX = params.x
                            initialY = params.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            if (Math.abs(dx) > 5 || Math.abs(dy) > 5) moved = true
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager?.updateViewLayout(overlayView, params)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (!moved) {
                                // Tap — trigger listening
                                wakeWordService?.startCommandListening()
                                setBackgroundColor(0xFF0F9D58.toInt())
                                postDelayed({
                                    setBackgroundColor(0xFFD32F2F.toInt())
                                }, 5000)
                            }
                            return true
                        }
                    }
                    return false
                }
            })
        }

        windowManager?.addView(button, params)
        overlayView = button
    }

    private fun bindToWakeWordService() {
        val intent = Intent(this, WakeWordService::class.java)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                wakeWordService = (service as WakeWordService.LocalBinder).getService()
                wakeWordService?.setOverlayBinder(binder)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                wakeWordService = null
            }
        }, Context.BIND_AUTO_CREATE)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaT OS Overlay")
            .setContentText("Floating button active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        super.onDestroy()
    }
}
