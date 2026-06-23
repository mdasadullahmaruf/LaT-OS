// app/src/main/java/com/lat/os/core/WakeWordService.kt
package com.lat.os.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.lat.os.engine.DecisionRouter
import java.util.Locale

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1
        var onCommandFinished: (() -> Unit)? = null
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var toneGen: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isListeningForCommand = false
    private val mainHandler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready — tap ✦ to speak"))
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
        DecisionRouter.init(this)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition not available")
        } else {
            updateNotification("Ready — tap ✦ to speak")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun createFreshRecognizer() {
        try { speechRecognizer?.destroy() } catch (e: Exception) { }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(params: Bundle?) {
                updateNotification("🎤 Listening...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                updateNotification("Processing...")
            }

            override fun onError(error: Int) {
                isListeningForCommand = false
                notifyOverlayIdle()

                // Error 11 = mic busy, retry after short delay
                if (error == 11 || error == SpeechRecognizer.ERROR_AUDIO) {
                    updateNotification("Mic busy, retrying...")
                    mainHandler.postDelayed({
                        startCommandListening()
                    }, 800)
                    return
                }

                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — try again"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error — check internet"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission needed"
                    else -> "Error ($error) — try again"
                }
                updateNotification(msg)
                if (error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error != SpeechRecognizer.ERROR_NO_MATCH) {
                    speak(msg)
                }
            }

            override fun onResults(results: Bundle?) {
                isListeningForCommand = false
                notifyOverlayIdle()
                val matches = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val command = matches?.firstOrNull()?.trim()?.lowercase()
                if (!command.isNullOrBlank()) {
                    updateNotification("Command: $command")
                    DecisionRouter.route(this@WakeWordService, command)
                } else {
                    speak("I didn't catch that. Please try again.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // Called from FloatingOverlay after TTS finishes speaking
    fun startCommandListening() {
        if (isListeningForCommand) return
        isListeningForCommand = true
        // Wait 600ms for TTS audio to fully release mic
        mainHandler.postDelayed({
            try {
                createFreshRecognizer()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                    )
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                        3000L
                    )
                    putExtra(
                        RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                        2000L
                    )
                }
                speechRecognizer?.startListening(intent)
                toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 200)
            } catch (e: Exception) {
                isListeningForCommand = false
                notifyOverlayIdle()
                speak("Could not start microphone.")
            }
        }, 600) // 600ms delay fixes Error 11
    }

    fun setOverlayBinder(b: FloatingOverlay.OverlayBinder?) { }

    private fun notifyOverlayIdle() {
        mainHandler.post { onCommandFinished?.invoke() }
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "wws_${System.currentTimeMillis()}")
        }
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
        try { speechRecognizer?.destroy() } catch (e: Exception) { }
        toneGen?.release()
        tts?.stop()
        tts?.shutdown()
        onCommandFinished = null
        super.onDestroy()
    }
}
