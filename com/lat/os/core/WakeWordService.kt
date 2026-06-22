// com/lat/os/core/WakeWordService.kt
package com.lat.os.core

import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.*
import androidx.core.app.NotificationCompat
import com.lat.os.engine.DecisionRouter
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream

class WakeWordService : Service() {

    companion object {
        const val CHANNEL_ID = "wake_word_channel"
        const val NOTIFICATION_ID = 1
        const val SAMPLE_RATE = 16000f
        const val MODEL_NAME = "vosk-model-small-en-us-0.15"
    }

    private lateinit var recognizer: Recognizer
    private var speechService: SpeechService? = null
    private val wakeWordDetected = AtomicBoolean(false)
    private val isListeningForCommand = AtomicBoolean(false)
    private var toneGen: ToneGenerator? = null
    private var overlayBinder: FloatingOverlay.OverlayBinder? = null
    private val handler = Handler(Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): WakeWordService = this@WakeWordService
    }
    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        toneGen = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification("Initialising…"))
        initVosk()
        return START_STICKY
    }

    // ---------- Vosk initialisation with automatic model download ----------
    private fun initVosk() {
        val modelDir = File(getExternalFilesDir(null), MODEL_NAME)
        val modelReady = modelDir.exists() && File(modelDir, "am").isDirectory && File(modelDir, "conf").isDirectory

        if (!modelReady) {
            updateNotification("Downloading speech model…")
            Thread {
                try {
                    downloadAndExtractModel(modelDir)
                    handler.post {
                        setupVosk(modelDir)
                    }
                } catch (e: Exception) {
                    handler.post {
                        updateNotification("Model download failed: ${e.message}")
                    }
                }
            }.start()
        } else {
            setupVosk(modelDir)
        }
    }

    private fun setupVosk(modelDir: File) {
        try {
            val model = Model(modelDir.absolutePath)
            recognizer = Recognizer(model, SAMPLE_RATE)
            speechService = SpeechService(recognizer, SAMPLE_RATE)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {}
                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { hyp ->
                        if (isListeningForCommand.get()) {
                            isListeningForCommand.set(false)
                            updateOverlayState(false)
                            DecisionRouter.route(this@WakeWordService, hyp.trim().lowercase())
                        } else if (hyp.lowercase().contains("hey phone")) {
                            wakeWordDetected.set(true)
                            toneGen?.startTone(ToneGenerator.TONE_PROP_ACK)
                            startCommandListening()
                        }
                    }
                }
                override fun onFinalResult(hypothesis: String?) {}
                override fun onError(exception: Exception?) {
                    handler.post {
                        updateNotification("Speech error: ${exception?.message}")
                    }
                }
                override fun onTimeout() {}
            })
            updateNotification("Assistant ready")
        } catch (e: IOException) {
            updateNotification("Vosk init failed: ${e.message}")
        }
    }

    // ---------- Model download & extraction ----------
    private fun downloadAndExtractModel(targetDir: File) {
        val zipUrl = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        val tempZip = File(cacheDir, "model.zip")

        // Download
        val url = URL(zipUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 120000
        connection.instanceFollowRedirects = true
        try {
            connection.inputStream.use { input ->
                tempZip.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        // Extract
        targetDir.mkdirs()
        ZipInputStream(tempZip.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val file = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    file.outputStream().use { fos ->
                        zis.copyTo(fos)
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        // Clean up temp zip
        tempZip.delete()
    }

    // ---------- Command listening control ----------
    fun startCommandListening() {
        if (isListeningForCommand.get()) return
        isListeningForCommand.set(true)
        updateOverlayState(true)
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP)
    }

    fun setOverlayBinder(binder: FloatingOverlay.OverlayBinder?) {
        this.overlayBinder = binder
    }

    private fun updateOverlayState(active: Boolean) {
        overlayBinder?.updateState(active)
    }

    // ---------- Notification helpers ----------
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

    // ---------- Service lifecycle ----------
    override fun onDestroy() {
        speechService?.stop()
        speechService?.shutdown()
        recognizer.close()
        toneGen?.release()
        super.onDestroy()
    }
}
