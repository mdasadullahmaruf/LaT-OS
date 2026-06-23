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
import android.os.IBinder
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
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var toneGen: ToneGenerator? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var overlayBinder: FloatingOverlay.OverlayBinder? = null
    private var isListeningForCommand = false

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Ready"))
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                ttsReady = true
            }
        }
        DecisionRouter.init(this)
        initSpeechRecognizer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition not available")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createListener())
        updateNotification("Ready — tap ✦ to speak")
    }

    private fun createListener() = object : RecognitionListener {
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
            overlayBinder?.updateState(false)
            FloatingOverlay.instance?.setIdleState()
            val msg = when (error) {
