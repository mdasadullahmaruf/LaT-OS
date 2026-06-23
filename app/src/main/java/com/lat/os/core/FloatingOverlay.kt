// app/src/main/java/com/lat/os/core/FloatingOverlay.kt
package com.lat.os.core

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.lat.os.engine.DecisionRouter
import java.util.Locale

class FloatingOverlay : Service(), TextToSpeech.OnInitListener {

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
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var toneGen: ToneGenerator? = null
    private var isListening = false

    inner class OverlayBinder : Binder() {
        fun updateState(active: Boolean) {
            isListening = active
            overlayView?.post {
                overlayView?.setBackgroundColor(
                    if (active) 0xFF0F9D58.toInt() else 0xFFD32F2F.toInt()
                )
                overlayView?.text = if (active) "🎤" else "✦"
            }
        }
    }

    private val binder = OverlayBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    private val wakeWordConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            wakeWordService = (service as WakeWordService.LocalBinder).getService()
            // Register idle callback — no circular reference
            WakeWordService.onCommandFinished = {
                setIdleState()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            wakeWordService = null
            WakeWordService.onCommandFinished = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeech(this, this)
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        DecisionRouter.init(this)
        createOverlayButton()
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

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            ttsReady = true
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
                            if (!hasMoved) onButtonTapped()
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

    private fun onButtonTapped() {
        if (isListening) {
            setIdleState()
            speak("Cancelled.")
            return
        }
        setActiveState()
        toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        overlayView?.postDelayed({
            speak("Yes, I'm listening.")
        }, 100)
        wakeWordService?.startCommandListening()
    }

    fun setActiveState() {
        isListening = true
        overlayView?.post {
            overlayView?.setBackgroundColor(0xFF0F9D58.toInt())
            overlayView?.text = "🎤"
        }
        // Safety auto-reset after 12 seconds
        overlayView?.postDelayed({
            if (isListening) setIdleState()
        }, 12000)
    }

    fun setIdleState() {
        isListening = false
        overlayView?.post {
            overlayView?.setBackgroundColor(0xFFD32F2F.toInt())
            overlayView?.text = "✦"
        }
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "overlay_${System.currentTimeMillis()}")
        } else {
            overlayView?.postDelayed({ speak(text) }, 600)
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
            .setContentText("Tap ✦ to give a voice command")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
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
            ).apply { description = "LaT OS floating button" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        instance = null
        WakeWordService.onCommandFinished = null
        tts?.stop()
        tts?.shutdown()
        toneGen?.release()
        try { overlayView?.let { windowManager?.removeView(it) } } catch (e: Exception) { }
        try { unbindService(wakeWordConnection) } catch (e: Exception) { }
        super.onDestroy()
    }
}
