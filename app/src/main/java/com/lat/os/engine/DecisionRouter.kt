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
        // Clean input
        val command = rawCommand
            .lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        SystemDatabase(context).logCommand(command, "processing")

        // Always try local first — fast and offline
        val localAction = SemanticParser.parse(command)
        if (localAction != null) {
            executeLocal(context, localAction)
            return
        }

        // If has API key — use Gemini NLU
        val apiKey = context
            .getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "")

        if (!apiKey.isNullOrBlank()) {
            scope.launch {
                val nlu = GeminiNLU.understand(context, command)
                if (nlu != null && nlu.confidence >= 0.6) {
                    executeNLU(context, nlu)
                } else {
                    tryAsAppName(context, command)
                }
            }
        } else {
            // No API — just try opening as app name
            tryAsAppName(context, command)
        }
    }

    private fun tryAsAppName(context: Context, command: String) {
        // Strip common words and try as app name
        val appQuery = command
            .replace("open", "")
            .replace("launch", "")
            .replace("start", "")
            .replace("run", "")
            .replace("go to", "")
            .replace("show me", "")
            .trim()

        if (appQuery.isNotBlank()) {
            val pkg = PackageMapper.findPackage(context, appQuery)
            if (pkg != null) {
                DeviceAutomator.openApp(context, pkg)
                speak("Opening $appQuery")
                return
            }
        }
        speak("Sorry, I didn't understand. Try saying open followed by the app name.")
    }

    private fun executeLocal(context: Context, action: com.lat.os.data.Action) {
        when (action.type) {
            "OPEN_APP" -> {
                val pkg = PackageMapper.findPackage(context, action.payload)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening ${action.payload}")
                } else {
                    // Try Gemini if available
                    val apiKey = context
                        .getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
                        .getString("gemini_api_key", "")
                    if (!apiKey.isNullOrBlank()) {
                        scope.launch {
                            val nlu = GeminiNLU.understand(
                                context, "open ${action.payload}")
                            if (nlu != null && nlu.intent == "LAUNCH_APP") {
                                val p = PackageMapper.findPackage(context, nlu.entity)
                                if (p != null) {
                                    DeviceAutomator.openApp(context, p)
                                    speak("Opening ${nlu.entity}")
                                } else {
                                    speak("${action.payload} is not installed.")
                                }
                            } else {
                                speak("${action.payload} is not installed.")
                            }
                        }
                    } else {
                        speak("${action.payload} is not installed on your phone.")
                    }
                }
            }
            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home") }
            "RECENTS" -> { DeviceAutomator.openRecents(); speak("Recent apps") }
            "SCROLL_DOWN" -> { DeviceAutomator.scrollDown(); speak("Scrolling down") }
            "SCROLL_UP" -> { DeviceAutomator.scrollUp(); speak("Scrolling up") }
            "SCROLL_LEFT" -> { DeviceAutomator.scrollLeft(); speak("Scrolling left") }
            "SCROLL_RIGHT" -> { DeviceAutomator.scrollRight(); speak("Scrolling right") }
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
                speak(if (ok) "Done" else "Couldn't find ${action.payload}")
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

    private suspend fun executeNLU(context: Context, nlu: NLUResult) {
        when (nlu.intent) {
            "LAUNCH_APP" -> {
                val pkg = PackageMapper.findPackage(context, nlu.entity)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening ${nlu.entity}")
                } else {
                    speak("${nlu.entity} is not installed.")
                }
            }
            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home") }
            "OPEN_RECENTS" -> { DeviceAutomator.openRecents() }
            "SCROLL_DOWN" -> { DeviceAutomator.scrollDown() }
            "SCROLL_UP" -> { DeviceAutomator.scrollUp() }
            "SCROLL_LEFT" -> { DeviceAutomator.scrollLeft() }
            "SCROLL_RIGHT" -> { DeviceAutomator.scrollRight() }
            "TAP_TEXT" -> DeviceAutomator.tapOnText(nlu.entity)
            "TAP_POSITION" -> DeviceAutomator.tapPosition(nlu.entity)
            "TYPE_TEXT" -> { DeviceAutomator.typeText(nlu.entity); speak("Typed") }
            "CALL_ANSWER" -> { DeviceAutomator.handleCallAction("answer"); speak("Answering") }
            "CALL_DECLINE" -> { DeviceAutomator.handleCallAction("decline"); speak("Declining") }
            "VOLUME_UP" -> { adjustVolume(context, true); speak("Volume up") }
            "VOLUME_DOWN" -> { adjustVolume(context, false); speak("Volume down") }
            "SCREENSHOT" -> { DeviceAutomator.takeScreenshot(); speak("Screenshot taken") }
            "OPEN_NOTIFICATIONS" -> DeviceAutomator.openNotifications()
            "OPEN_SETTINGS" -> DeviceAutomator.openQuickSettings()
            "ANSWER_QUESTION" -> {
                val result = GeminiClient.askGemini(
                    context, nlu.entity, ScreenScraper.scrapeScreen())
                speak(result?.spoken_response ?: "I don't know.")
            }
            else -> speak("I'm not sure how to do that.")
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
