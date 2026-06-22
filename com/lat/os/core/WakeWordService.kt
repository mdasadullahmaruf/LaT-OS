// com/lat/os/core/WakeWordService.kt
package com.lat.os.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.lat.os.engine.DecisionRouter
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1
        const val SAMPLE_RATE = 16000f
    }

    private lateinit var recognizer: Recognizer
    private var speechService: SpeechService? = null
    private var wakeWordDetected = AtomicBoolean(false)
    private var isListeningForCommand = AtomicBoolean(false)
    private var toneGen: ToneGenerator? = null
    private var overlayBinder: FloatingOverlay.OverlayBinder? = null

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        toneGen = ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 80)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Idle"))
        initVosk()
        return START_STICKY
    }

    private fun initVosk() {
        val modelDir = File(getExternalFilesDir(null), "vosk-model-small-en-us-0.15")
        if (!modelDir.exists()) {
            // In a real deployment, download the model here; for now, log.
            updateNotification("Model not found, please place model at ${modelDir.absolutePath}")
            return
        }

        try {
            val model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let {
                        if (isListeningForCommand.get()) {
                            isListeningForCommand.set(false)
                            updateOverlayState(false)
                            DecisionRouter.route(this@WakeWordService, it.trim().lowercase())
                        } else if (it.lowercase().contains("hey phone")) {
                            wakeWordDetected.set(true)
                            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK)
                            startCommandListening()
                        }
                    }
                }
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {}
                override fun onTimeout() {}
            })
        } catch (e: IOException) {
            e.printStackTrace()
            updateNotification("Vosk init failed: ${e.message}")
        }
    }

    fun startCommandListening() {
        if (isListeningForCommand.get()) return
        isListeningForCommand.set(true)
        updateOverlayState(true)
        // Restart listening to capture command (SpeechService is already running)
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP)
    }

    fun setOverlayBinder(binder: FloatingOverlay.OverlayBinder?) {
        this.overlayBinder = binder
    }

    private fun updateOverlayState(active: Boolean) {
        overlayBinder?.updateState(active)
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaT OS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Voice Service", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        recognizer.close()
        toneGen?.release()
        super.onDestroy()
    }
}
