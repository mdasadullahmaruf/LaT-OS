// app/src/main/java/com/lat/os/core/WakeWordService.kt
package com.lat.os.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.lat.os.engine.DecisionRouter
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1
        const val SAMPLE_RATE = 16000f
        const val WAKE_WORD = "hey phone"
    }

    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var toneGen: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var overlayBinder: FloatingOverlay.OverlayBinder? = null
    private val isListeningForCommand = AtomicBoolean(false)

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.getDefault()
            }
        }
        DecisionRouter.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Listening for 'Hey Phone'..."))
        initVosk()
        return START_STICKY
    }

    private fun initVosk() {
        val modelDir = File(getExternalFilesDir(null), "vosk-model-small-en-us-0.15")
        if (!modelDir.exists()) {
            updateNotification("⚠ Vosk model missing — tap button to use manually")
            speak("Wake word unavailable. Tap the floating button to give commands.")
            return
        }
        try {
            val model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer!!, SAMPLE_RATE)
            speechService?.startListening(createListener())
            updateNotification("🎤 Listening for 'Hey Phone'")
        } catch (e: IOException) {
            updateNotification("Vosk error: ${e.message}")
        }
    }

    private fun createListener() = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            hypothesis ?: return
            // Check partial results too for faster response
            if (!isListeningForCommand.get() &&
                hypothesis.lowercase().contains(WAKE_WORD)) {
                onWakeWordDetected()
            }
        }

        override fun onResult(hypothesis: String?) {
            hypothesis ?: return
            val text = hypothesis.lowercase()
            if (isListeningForCommand.get()) {
                // This is the command after wake word
                isListeningForCommand.set(false)
                overlayBinder?.updateState(false)
                // Extract actual text from Vosk JSON
                val cleaned = text
                    .replace("{\"text\" : \"", "")
                    .replace("\"}", "")
                    .trim()
                if (cleaned.isNotBlank() && cleaned != WAKE_WORD) {
                    DecisionRouter.route(this@WakeWordService, cleaned)
                }
            } else if (text.contains(WAKE_WORD)) {
                onWakeWordDetected()
            }
        }

        override fun onFinalResult(hypothesis: String?) {}
        override fun onError(exception: Exception?) {
            updateNotification("STT Error: ${exception?.message}")
        }
        override fun onTimeout() {
            if (isListeningForCommand.get()) {
                isListeningForCommand.set(false)
                overlayBinder?.updateState(false)
                speak("I didn't catch that. Try again.")
            }
        }
    }

    private fun onWakeWordDetected() {
        toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 200)
        speak("Yes?")
        startCommandListening()
    }

    fun startCommandListening() {
        if (isListeningForCommand.get()) return
        isListeningForCommand.set(true)
        overlayBinder?.updateState(true)
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        updateNotification("🟢 Listening for command...")
    }

    fun setOverlayBinder(b: FloatingOverlay.OverlayBinder?) {
        overlayBinder = b
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wws_${System.currentTimeMillis()}")
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaT OS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Voice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        recognizer?.close()
        toneGen?.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
