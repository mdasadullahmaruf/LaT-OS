// app/src/main/java/com/lat/os/engine/DecisionRouter.kt
package com.lat.os.engine

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.lat.os.automation.DeviceAutomator
import com.lat.os.automation.ScreenScraper
import com.lat.os.data.SystemDatabase
import kotlinx.coroutines.*
import java.util.Locale

object DecisionRouter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun init(context: Context) {
        if (tts == null) {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ttsReady = true
                }
            }
        }
    }

    fun route(context: Context, rawCommand: String) {
        val command = rawCommand.lowercase().trim()
        SystemDatabase(context).logCommand(command, "processing...")

        scope.launch {
            // Step 1: Try Gemini NLU first (handles all accent/casing issues)
            val apiKey = context.getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
                .getString("gemini_api_key", "")

            if (!apiKey.isNullOrBlank()) {
                val nlu = GeminiNLU.understand(context, command)
                if (nlu != null && nlu.confidence >= 0.7) {
                    executeNLU(context, nlu)
                    return@launch
                }
            }

            // Step 2: Fallback to local parser if no API key or low confidence
            val localAction = SemanticParser.parse(command)
            if (localAction != null) {
                executeLocal(context, localAction)
            } else {
                speak("I'm not sure what you mean. Please try again.")
            }
        }
    }

    private suspend fun executeNLU(context: Context, nlu: NLUResult) {
        val entity = nlu.entity.trim()

        when (nlu.intent) {

            "LAUNCH_APP" -> {
                val pkg = PackageMapper.findPackage(context, entity)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening $entity")
                } else {
                    speak("I couldn't find $entity on your phone.")
                }
            }

            "GO_BACK" -> {
                DeviceAutomator.pressBack()
                speak("Going back")
            }

            "GO_HOME" -> {
                DeviceAutomator.pressHome()
                speak("Home screen")
            }

            "OPEN_RECENTS" -> {
                DeviceAutomator.openRecents()
                speak("Recent apps")
            }

            "SCROLL_DOWN" -> {
                DeviceAutomator.scrollDown()
                speak("Scrolling down")
            }

            "SCROLL_UP" -> {
                DeviceAutomator.scrollUp()
                speak("Scrolling up")
            }

            "SCROLL_LEFT" -> {
                DeviceAutomator.scrollLeft()
                speak("Scrolling left")
            }

            "SCROLL_RIGHT" -> {
                DeviceAutomator.scrollRight()
                speak("Scrolling right")
            }

            "TAP_TEXT" -> {
                val ok = DeviceAutomator.tapOnText(entity)
                speak(if (ok) "Tapped $entity" else "Couldn't find $entity on screen")
            }

            "TAP_POSITION" -> {
                DeviceAutomator.tapPosition(entity)
                speak("Tapping ${entity.replace("_", " ")}")
            }

            "TYPE_TEXT" -> {
                DeviceAutomator.typeText(entity)
                speak("Typed $entity")
            }

            "SEARCH_QUERY" -> {
                DeviceAutomator.typeText(entity)
                speak("Searching for $entity")
            }

            "CALL_ANSWER" -> {
                DeviceAutomator.handleCallAction("answer")
                speak("Answering call")
            }

            "CALL_DECLINE" -> {
                DeviceAutomator.handleCallAction("decline")
                speak("Declining call")
            }

            "VOLUME_UP" -> {
                adjustVolume(context, true)
                speak("Volume up")
            }

            "VOLUME_DOWN" -> {
                adjustVolume(context, false)
                speak("Volume down")
            }

            "SCREENSHOT" -> {
                DeviceAutomator.takeScreenshot()
                speak("Screenshot taken")
            }

            "OPEN_NOTIFICATIONS" -> {
                DeviceAutomator.openNotifications()
                speak("Opening notifications")
            }

            "OPEN_SETTINGS" -> {
                DeviceAutomator.openQuickSettings()
                speak("Opening settings")
            }

            "MEDIA_PLAY" -> {
                // Try to find and tap play button on screen
                val ok = DeviceAutomator.tapOnText("Play")
                if (!ok) DeviceAutomator.tapOnText("▶")
                speak("Playing")
            }

            "MEDIA_PAUSE" -> {
                val ok = DeviceAutomator.tapOnText("Pause")
                if (!ok) DeviceAutomator.tapOnText("⏸")
                speak("Paused")
            }

            "MEDIA_NEXT" -> {
                DeviceAutomator.tapOnText("Next")
                speak("Next track")
            }

            "MEDIA_PREV" -> {
                DeviceAutomator.tapOnText("Previous")
                speak("Previous track")
            }

            "ANSWER_QUESTION" -> {
                // Use Gemini to answer the question
                val screenData = ScreenScraper.scrapeScreen()
                val result = GeminiClient.askGemini(context, entity, screenData)
                speak(result?.spoken_response ?: "I don't know the answer to that.")
            }

            "UNKNOWN" -> speak("I didn't understand that. Could you rephrase?")

            else -> speak("I'm not sure how to do that yet.")
        }

        SystemDatabase(context).logCommand(
            "${nlu.intent}: $entity",
            "confidence: ${nlu.confidence}"
        )
    }

    private fun executeLocal(context: Context, action: com.lat.os.data.Action) {
        when (action.type) {
            "OPEN_APP" -> {
                val pkg = PackageMapper.findPackage(context, action.payload)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening ${action.payload}")
                } else {
                    speak("Couldn't find ${action.payload}")
                }
            }
            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home screen") }
            "RECENTS" -> { DeviceAutomator.openRecents(); speak("Recent apps") }
            "SCROLL_DOWN" -> { DeviceAutomator.scrollDown(); speak("Scrolling down") }
            "SCROLL_UP" -> { DeviceAutomator.scrollUp(); speak("Scrolling up") }
            "SCROLL_LEFT" -> { DeviceAutomator.scrollLeft() }
            "SCROLL_RIGHT" -> { DeviceAutomator.scrollRight() }
            "TAP_POSITION" -> { DeviceAutomator.tapPosition(action.payload) }
            "CALL_ACTION" -> { DeviceAutomator.handleCallAction(action.payload) }
            "TAP_TEXT" -> { DeviceAutomator.tapOnText(action.payload) }
            "TYPE_TEXT" -> { DeviceAutomator.typeText(action.payload) }
            "VOLUME_UP" -> { adjustVolume(context, true); speak("Volume up") }
            "VOLUME_DOWN" -> { adjustVolume(context, false); speak("Volume down") }
            "SCREENSHOT" -> { DeviceAutomator.takeScreenshot(); speak("Screenshot taken") }
            "OPEN_NOTIFICATIONS" -> { DeviceAutomator.openNotifications() }
            "OPEN_QUICK_SETTINGS" -> { DeviceAutomator.openQuickSettings() }
            else -> speak("I don't know how to do that.")
        }
    }

    private fun adjustVolume(context: Context, up: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "router_${System.currentTimeMillis()}")
        }
    }
}
