// app/src/main/java/com/lat/os/engine/DecisionRouter.kt
package com.lat.os.engine

import android.content.Context
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import com.lat.os.automation.DeviceAutomator
import com.lat.os.automation.ScreenScraper
import com.lat.os.data.Action
import com.lat.os.data.SystemDatabase
import kotlinx.coroutines.*
import java.util.Locale

object DecisionRouter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
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
        // Normalize — lowercase, trim, remove punctuation
        val command = rawCommand.lowercase()
            .trim()
            .replace(Regex("[^a-z0-9 ]"), "")

        SystemDatabase(context).logCommand(command, "processing...")

        val localAction = SemanticParser.parse(command)
        if (localAction != null) {
            executeLocal(context, localAction)
        } else {
            // Send to Gemini with full screen context
            scope.launch {
                speak("Let me think...")
                val screenData = ScreenScraper.scrapeScreen()
                val result = GeminiClient.askGemini(context, command, screenData)
                if (result != null) {
                    executeGeminiResult(context, result)
                } else {
                    // Last resort — try to open as app name
                    val pkg = PackageMapper.findPackage(context, command
                        .replace("open ", "")
                        .replace("launch ", "")
                        .replace("start ", "")
                        .trim())
                    if (pkg != null) {
                        DeviceAutomator.openApp(context, pkg)
                        speak("Opening that now")
                    } else {
                        speak("I'm not sure how to do that. Please try again.")
                    }
                }
            }
        }
    }

    private fun executeLocal(context: Context, action: Action) {
        when (action.type) {

            "OPEN_APP" -> {
                // Normalize the app name — remove extra words, fix case
                val appQuery = action.payload
                    .lowercase()
                    .trim()
                    .replace(Regex("[^a-z0-9 ]"), "")
                val pkg = PackageMapper.findPackage(context, appQuery)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening ${action.payload}")
                } else {
                    // Try Gemini to figure out the app
                    scope.launch {
                        val screenData = ScreenScraper.scrapeScreen()
                        val result = GeminiClient.askGemini(
                            context,
                            "open the app called ${action.payload}",
                            screenData
                        )
                        if (result != null) {
                            executeGeminiResult(context, result)
                        } else {
                            speak("I couldn't find ${action.payload} on your phone.")
                        }
                    }
                }
            }

            "GO_BACK" -> { DeviceAutomator.pressBack(); speak("Going back") }
            "GO_HOME" -> { DeviceAutomator.pressHome(); speak("Home screen") }
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
                when (action.payload) {
                    "answer", "left" -> speak("Answering call")
                    "decline", "right" -> speak("Declining call")
                }
            }

            "TAP_TEXT" -> {
                val ok = DeviceAutomator.tapOnText(action.payload)
                if (!ok) {
                    // Try case-insensitive search via Gemini
                    scope.launch {
                        val screen = ScreenScraper.scrapeScreen()
                        val result = GeminiClient.askGemini(
                            context,
                            "tap on ${action.payload}",
                            screen
                        )
                        if (result != null) executeGeminiResult(context, result)
                        else speak("Couldn't find ${action.payload} on screen")
                    }
                } else {
                    speak("Tapped ${action.payload}")
                }
            }

            "TYPE_TEXT" -> {
                DeviceAutomator.typeText(action.payload)
                speak("Typed ${action.payload}")
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

            "OPEN_QUICK_SETTINGS" -> {
                DeviceAutomator.openQuickSettings()
                speak("Opening quick settings")
            }

            else -> {
                // Unknown local action — send to Gemini
                scope.launch {
                    val screen = ScreenScraper.scrapeScreen()
                    val result = GeminiClient.askGemini(context, action.payload, screen)
                    if (result != null) executeGeminiResult(context, result)
                    else speak("I don't know how to do that yet.")
                }
            }
        }
    }

    private fun executeGeminiResult(context: Context, result: GeminiResponse) {
        when (result.action?.uppercase()) {
            "TAP" -> {
                val target = result.target_text ?: ""
                val ok = DeviceAutomator.tapOnText(target)
                speak(if (ok) result.spoken_response ?: "Done"
                      else "Couldn't find $target on screen")
            }
            "TYPE" -> {
                DeviceAutomator.typeText(result.payload ?: "")
                speak(result.spoken_response ?: "Typed")
            }
            "SCROLL" -> {
                when (result.payload?.lowercase()) {
                    "up" -> DeviceAutomator.scrollUp()
                    "left" -> DeviceAutomator.scrollLeft()
                    "right" -> DeviceAutomator.scrollRight()
                    else -> DeviceAutomator.scrollDown()
                }
                speak(result.spoken_response ?: "Scrolled")
            }
            "OPEN" -> {
                val pkg = PackageMapper.findPackage(
                    context, result.target_text ?: "")
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak(result.spoken_response ?: "Opening")
                } else {
                    speak("Couldn't find that app")
                }
            }
            "ANSWER" -> speak(result.spoken_response ?: "")
            else -> speak(result.spoken_response ?: "Done")
        }
        SystemDatabase(context).logCommand(
            result.action ?: "",
            result.spoken_response ?: ""
        )
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
