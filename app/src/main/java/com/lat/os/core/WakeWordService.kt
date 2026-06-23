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
import android.os.PowerManager
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
        const val WAKE_WORD = "hey phone"
        var onCommandFinished: (() -> Unit)? = null
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var toneGen: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var isCommandMode = false
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // How long to wait before restarting wake word listening
    private val RESTART_DELAY_MS = 500L
    private val COMMAND_RESTART_DELAY_MS = 1500L

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("👂 Listening for 'Hey Phone'"))
        acquireWakeLock()
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 90)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
        DecisionRouter.init(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            mainHandler.postDelayed({ startWakeWordListening() }, 1000)
        }
        return START_STICKY
    }

    // ── WAKE WORD LOOP ───────────────────────────────────────────

    private fun startWakeWordListening() {
        if (!isRunning || isCommandMode) return
        updateNotification("👂 Listening for 'Hey Phone'")
        buildAndStartRecognizer(isWakeWordMode = true)
    }

    private fun buildAndStartRecognizer(isWakeWordMode: Boolean) {
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) { }

            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                updateNotification("❌ Speech recognition not available")
                return@post
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(
                if (isWakeWordMode) wakeWordListener else commandListener
            )

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                if (isWakeWordMode) {
                    // Shorter silence timeout for wake word
                    putExtra(RecognizerIntent
                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                    putExtra(RecognizerIntent
                        .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1000L)
                } else {
                    // Longer for commands
                    putExtra(RecognizerIntent
                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                    putExtra(RecognizerIntent
                        .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
                }
            }

            try {
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                mainHandler.postDelayed({
                    if (isWakeWordMode) startWakeWordListening()
                }, RESTART_DELAY_MS)
            }
        }
    }

    // ── WAKE WORD LISTENER ───────────────────────────────────────

    private val wakeWordListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { }
        override fun onBeginningOfSpeech() { }
        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) { }
        override fun onEndOfSpeech() { }
        override fun onEvent(eventType: Int, params: Bundle?) { }

        override fun onPartialResults(partialResults: Bundle?) {
            // Check partial results for wake word — faster response
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.lowercase() ?: return
            if (containsWakeWord(partial)) {
                onWakeWordDetected()
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.joinToString(" ")?.lowercase() ?: ""
            if (containsWakeWord(text)) {
                onWakeWordDetected()
            } else {
                // Not wake word — restart listening
                mainHandler.postDelayed({
                    startWakeWordListening()
                }, RESTART_DELAY_MS)
            }
        }

        override fun onError(error: Int) {
            // Most errors just mean silence or timeout — restart quietly
            val delay = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> 1000L
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> 3000L
                SpeechRecognizer.ERROR_SERVER -> 2000L
                else -> RESTART_DELAY_MS
            }
            mainHandler.postDelayed({
                if (!isCommandMode) startWakeWordListening()
            }, delay)
        }
    }

    // ── COMMAND LISTENER ─────────────────────────────────────────

    private val commandListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            updateNotification("🎤 Listening for command...")
        }
        override fun onBeginningOfSpeech() { }
        override fun onRmsChanged(rmsdB: Float) { }
        override fun onBufferReceived(buffer: ByteArray?) { }
        override fun onEndOfSpeech() {
            updateNotification("⚙ Processing...")
        }
        override fun onEvent(eventType: Int, params: Bundle?) { }
        override fun onPartialResults(partialResults: Bundle?) { }

        override fun onResults(results: Bundle?) {
            isCommandMode = false
            onCommandFinished?.invoke()

            val matches = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val command = matches?.firstOrNull()?.trim()?.lowercase()

            if (!command.isNullOrBlank()) {
                updateNotification("💬 Command: $command")
                DecisionRouter.route(this@WakeWordService, command)
            } else {
                speak("I didn't catch that.")
            }

            // Restart wake word listening after command
            mainHandler.postDelayed({
                startWakeWordListening()
            }, COMMAND_RESTART_DELAY_MS)
        }

        override fun onError(error: Int) {
            isCommandMode = false
            onCommandFinished?.invoke()

            // Error 11 = mic busy, retry
            if (error == 11 || error == SpeechRecognizer.ERROR_AUDIO) {
                mainHandler.postDelayed({
                    startCommandListening()
                }, 800)
                return
            }

            if (error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                error != SpeechRecognizer.ERROR_NO_MATCH) {
                speak("Sorry, try again.")
            }

            mainHandler.postDelayed({
                startWakeWordListening()
            }, COMMAND_RESTART_DELAY_MS)
        }
    }

    // ── WAKE WORD DETECTION ──────────────────────────────────────

    private fun containsWakeWord(text: String): Boolean {
        val t = text.lowercase().trim()
        return t.contains("hey phone") ||
               t.contains("hey phone") ||
               t.contains("a phone") ||   // mishear fallback
               t.contains("hey fo") ||    // partial match
               t.contains("ay phone") ||  // partial match
               t.contains("hey phon")     // partial match
    }

    private fun onWakeWordDetected() {
        if (isCommandMode) return
        toneGen?.startTone(ToneGenerator.TONE_PROP_ACK, 300)
        // Small delay so beep finishes before TTS
        mainHandler.postDelayed({
            speak("Yes?") {
                // Start command listening AFTER TTS finishes
                mainHandler.postDelayed({
                    startCommandListening()
                }, 700)
            }
        }, 100)
    }

    // ── COMMAND MODE ─────────────────────────────────────────────

    fun startCommandListening() {
        if (isCommandMode) return
        isCommandMode = true
        updateNotification("🎤 Listening for command...")
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        mainHandler.postDelayed({
            buildAndStartRecognizer(isWakeWordMode = false)
        }, 600) // Wait for mic to be free
    }

    fun setOverlayBinder(b: FloatingOverlay.OverlayBinder?) { }

    // ── TTS WITH CALLBACK ────────────────────────────────────────

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!ttsReady) {
            mainHandler.postDelayed({ speak(text, onDone) }, 500)
            return
        }
        val id = "wws_${System.currentTimeMillis()}"
        if (onDone != null) {
            tts?.setOnUtteranceProgressListener(object :
                android.speech.tts.UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId == id) {
                        mainHandler.post { onDone() }
                    }
                }
                override fun onError(utteranceId: String?) {
                    mainHandler.post { onDone() }
                }
            })
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun speakPublic(text: String) = speak(text)

    // ── WAKE LOCK ────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LaT OS::WakeWordLock"
        ).apply { acquire(24 * 60 * 60 * 1000L) } // 24 hours
    }

    // ── NOTIFICATIONS ────────────────────────────────────────────

    private fun updateNotification(text: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LaT OS")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Voice Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "LaT OS wake word listener" }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ── CLEANUP ──────────────────────────────────────────────────

    override fun onDestroy() {
        isRunning = false
        isCommandMode = false
        mainHandler.removeCallbacksAndMessages(null)
        try { speechRecognizer?.destroy() } catch (e: Exception) { }
        try { wakeLock?.release() } catch (e: Exception) { }
        toneGen?.release()
        tts?.stop()
        tts?.shutdown()
        onCommandFinished = null
        super.onDestroy()
    }
}
