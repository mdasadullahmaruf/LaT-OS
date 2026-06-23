// app/src/main/java/com/lat/os/engine/DecisionRouter.kt
package com.lat.os.engine

import android.content.Context
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
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    ttsReady = true
                }
            }
        }
    }

    fun route(context: Context, command: String) {
        SystemDatabase(context).logCommand(command, "processing...")
        val localAction = SemanticParser.parse(command)
        if (localAction != null) {
            executeLocal(context, localAction)
        } else {
            scope.launch {
                val screenData = ScreenScraper.scrapeScreen()
                val result = GeminiClient.askGemini(context, command, screenData)
                if (result != null) {
                    executeGeminiResult(context, result)
                } else {
                    speak("Sorry, I couldn't process that command.")
                }
            }
        }
    }

    private fun executeLocal(context: Context, action: com.lat.os.data.Action) {
        when (action.type) {

            "OPEN_APP" -> {
                val pkg = PackageMapper.findPackage(context, action.payload)
                if (pkg != null) {
                    DeviceAutomator.openApp(context, pkg)
                    speak("Opening ${action.payload}")
                } else {
                    speak("I couldn't find ${action.payload} on your phone.")
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

            "RECENTS" -> {
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

            "TAP_POSITION" -> {
                DeviceAutomator.tapPosition(action.payload)
                val readablePos = action.payload.replace("_", " ")
                speak("Tapping $readablePos")
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
                speak(if (ok) "Tapped ${action.payload}"
                      else "Couldn't find ${action.payload} on screen")
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

            "BRIGHTNESS_UP" -> speak("Please adjust brightness manually in quick settings")

            "BRIGHTNESS_DOWN" -> speak("Please adjust brightness manually in quick settings")

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

            else -> speak("I don't know how to do that yet.")
        }
    }

    private fun executeGeminiResult(context: Context, result: GeminiResponse) {
        when (result.action) {
            "TAP" -> {
                val ok = DeviceAutomator.tapOnText(result.target_text ?: "")
                speak(if (ok) result.spoken_response
                      else "Couldn't find ${result.target_text} on screen")
            }
            "TYPE" -> {
                DeviceAutomator.typeText(result.payload ?: "")
                speak(result.spoken_response)
            }
            "SCROLL" -> {
                DeviceAutomator.scrollDown()
                speak(result.spoken_response)
            }
            "ANSWER" -> speak(result.spoken_response)
            else -> speak(result.spoken_response)
        }
        SystemDatabase(context).logCommand(result.action ?: "", result.spoken_response ?: "")
    }

    private fun adjustVolume(context: Context, up: Boolean) {
        val audio = context.getSystemService(Context.AUDIO_SERVICE)
                as android.media.AudioManager
        audio.adjustStreamVolume(
            android.media.AudioManager.STREAM_MUSIC,
            if (up) android.media.AudioManager.ADJUST_RAISE
            else android.media.AudioManager.ADJUST_LOWER,
            android.media.AudioManager.FLAG_SHOW_UI
        )
    }

    private fun speak(text: String) {
        if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "router_${System.currentTimeMillis()}")
        }
    }
}
