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
        val command = rawCommand
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        SystemDatabase(context).logCommand(command, "processing")

        // Check API key
        val apiKey = context
            .getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "")

        if (!apiKey.isNullOrBlank()) {
            // Use Gemini NLU for smart understanding
            scope.launch {
                try {
                    val nlu = GeminiNLU.understand(context, command)
                    if (nlu != null && nlu.confidence >= 0.6) {
                        executeNLU(context, nlu)
                    } else {
                        // NLU failed — try local parser
                        val local = SemanticParser.parse(command)
                        if (local != null) {
                            executeLocal(context, local)
                        } else {
                            tryAsAppName(context, command)
                        }
                    }
                } catch (e: Exception) {
                    val local = SemanticParser.parse(command)
                    if (local != null) {
                        executeLocal(context, local)
                    } else {
                        tryAsAppName(context, command)
                    }
                }
            }
        } else {
            // No API key — use local parser only
            val local = SemanticParser.parse(command)
            if (local != null) {
                executeLocal(context, local)
            } else {
                tryAsAppName(context, command)
            }
        }
    }

    private fun tryAsAppName(context: Context, command: String) {
        val appQuery = command
            .replace("open", "")
            .replace("launch", "")
            .replace("start", "")
            .replace("run", "")
            .replace("show", "")
            .trim()

        if (appQuery.isNotBlank()) {
            val pkg = PackageMapper.findPackage(context, appQuery)
            if (pkg != null) {
                DeviceAutomator.openApp(context, pkg)
                speak("Opening $appQuery")
                return
            }
        }
        speak("Sorry I didn't understand. Try saying open followed by the app name.")
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
                    speak("$entity is not installed on your phone.")
                }
            }
            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home") }
            "OPEN_RECENTS" -> { DeviceAutomator.openRecents(); speak("Recent apps") }
            "SCROLL_DOWN" -> { DeviceAutomator.scrollDown(); speak("Scrolling down") }
            "SCROLL_UP" -> { DeviceAutomator.scrollUp(); speak("Scrolling up") }
            "SCROLL_LEFT" -> { DeviceAutomator.scrollLeft(); speak("Scrolling left") }
            "SCROLL_RIGHT" -> { DeviceAutomator.scrollRight(); speak("Scrolling right") }
            "TAP_TEXT" -> {
                val ok = DeviceAutomator.tapOnText(entity)
                speak(if (ok) "Done" else "Could not find $entity on screen")
            }
            "TAP_POSITION" -> {
                DeviceAutomator.tapPosition(entity)
                speak("Tapping ${entity.replace("_", " ")}")
            }
            "TYPE_TEXT" -> {
                DeviceAutomator.typeText(entity)
                speak("Typed")
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
            "VOLUME_UP" -> { adjustVolume(context, true); speak("Volume up") }
            "VOLUME_DOWN" -> { adjustVolume(context, false); speak("Volume down") }
            "SCREENSHOT" -> { DeviceAutomator.takeScreenshot(); speak("Screenshot taken") }
            "OPEN_NOTIFICATIONS" -> {
                DeviceAutomator.openNotifications()
                speak("Opening notifications")
            }
            "OPEN_SETTINGS" -> {
                DeviceAutomator.openQuickSettings()
                speak("Opening settings")
            }
            "MEDIA_PLAY" -> {
                DeviceAutomator.tapOnText("Play")
                speak("Playing")
            }
            "MEDIA_PAUSE" -> {
                DeviceAutomator.tapOnText("Pause")
                speak("Paused")
            }
            "MEDIA_NEXT" -> {
                DeviceAutomator.tapOnText("Next")
                speak("Next")
            }
            "MEDIA_PREV" -> {
                DeviceAutomator.tapOnText("Previous")
                speak("Previous")
            }
            "ANSWER_QUESTION" -> {
                val screen = ScreenScraper.scrapeScreen()
                val result = GeminiClient.askGemini(context, entity, screen)
                speak(result?.spoken_response ?: "I don't know the answer.")
            }
            "UNKNOWN" -> {
                speak("I didn't understand that. Please try again.")
            }
            else -> speak("I'm not sure how to do that.")
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
                    speak("${action.payload} is not installed.")
                }
            }
            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home") }
            "RECENTS" -> { DeviceAutomator.openRecents(); speak("Recent apps") }
            "SCROLL_DOWN" -> { DeviceAutomator.scrollDown(); speak("Scrolling down") }
            "SCROLL_UP" -> { DeviceAutomator.scrollUp(); speak("Scrolling up") }
            "SCROLL_LEFT" -> { DeviceAutomator.scrollLeft() }
            "SCROLL_RIGHT" -> { DeviceAutomator.scrollRight() }
            "TAP_POSITION" -> {
                DeviceAutomator.tapPosition(action.payload)
                speak("Tapping ${action.payload.replace("_", " ")}")
            }
            "CALL_ACTION" -> {
                DeviceAutomator.handleCallAction(action.payload)
                speak(if (action.payload == "answer") "Answering" else "Declining")
            }
            "TAP_TEXT" -> {
                val ok = DeviceAutomator.tapOnText(action.payload)
                speak(if (ok) "Done" else "Could not find ${action.payload}")
            }
            "TYPE_TEXT" -> {
                DeviceAutomator.typeText(action.payload)
                speak("Typed")
            }
            "VOLUME_UP" -> { adjustVolume(context, true); speak("Volume up") }
            "VOLUME_DOWN" -> { adjustVolume(context, false); speak("Volume down") }
            "SCREENSHOT" -> { DeviceAutomator.takeScreenshot(); speak("Done") }
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
            tts?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "router_${System.currentTimeMillis()}"
            )
        }
    }
}
