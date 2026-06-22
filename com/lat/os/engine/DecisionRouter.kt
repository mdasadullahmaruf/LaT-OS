// com/lat/os/engine/DecisionRouter.kt
package com.lat.os.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import com.lat.os.automation.DeviceAutomator
import com.lat.os.automation.ScreenScraper
import com.lat.os.data.SystemDatabase
import kotlinx.coroutines.*

object DecisionRouter {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var tts: TextToSpeech? = null

    fun init(context: Context) {
        tts = TextToSpeech(context) { status -> /* no-op */ }
    }

    fun route(context: Context, command: String) {
        // Log to history
        SystemDatabase(context).logCommand(command, "processing...")

        val localAction = SemanticParser.parse(command)
        if (localAction != null) {
            executeLocal(context, localAction)
        } else {
            // Complex -> Gemini
            scope.launch {
                val screenData = ScreenScraper.scrapeScreen()
                val result = GeminiClient.askGemini(context, command, screenData)
                if (result != null) {
                    executeGeminiResult(context, result)
                } else {
                    speak("Sorry, I couldn't process that.")
                }
            }
        }
    }

    private fun executeLocal(context: Context, action: com.lat.os.data.Action) {
        when (action.type) {
            "OPEN_APP" -> {
                val packageName = PackageMapper.findPackage(context, action.payload)
                if (packageName != null) {
                    DeviceAutomator.openApp(context, packageName)
                    speak("Opening ${action.payload}")
                } else {
                    speak("App not found: ${action.payload}")
                }
            }
            "GO_BACK" -> {
                DeviceAutomator.pressBack()
                speak("Going back")
            }
            "GO_HOME" -> {
                DeviceAutomator.pressHome()
                speak("Home")
            }
            "SCROLL_DOWN" -> {
                DeviceAutomator.scrollDown()
                speak("Scrolling down")
            }
            "SCROLL_UP" -> {
                DeviceAutomator.scrollUp()
                speak("Scrolling up")
            }
            "TAP_TEXT" -> {
                val success = DeviceAutomator.tapOnText(action.payload)
                speak(if (success) "Tapped ${action.payload}" else "Couldn't find ${action.payload}")
            }
            "TYPE_TEXT" -> {
                DeviceAutomator.typeText(action.payload)
                speak("Typed")
            }
            else -> speak("Unknown offline command")
        }
    }

    private fun executeGeminiResult(context: Context, result: GeminiResponse) {
        when (result.action) {
            "TAP" -> {
                val success = DeviceAutomator.tapOnText(result.target_text ?: "")
                speak(if (success) result.spoken_response else "Couldn't tap ${result.target_text}")
            }
            "TYPE" -> {
                DeviceAutomator.typeText(result.payload ?: "")
                speak(result.spoken_response)
            }
            "SCROLL" -> {
                // Assume scroll direction from payload
                DeviceAutomator.scrollDown()
                speak(result.spoken_response)
            }
            "ANSWER" -> {
                speak(result.spoken_response)
            }
            else -> speak(result.spoken_response)
        }
        SystemDatabase(context).logCommand(result.toString(), result.spoken_response)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "latos_${System.currentTimeMillis()}")
    }
}
