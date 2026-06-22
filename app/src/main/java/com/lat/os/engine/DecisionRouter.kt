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
        val target = result.target_text ?: ""
        val payload = result.payload ?: ""
        val spoken = result.spoken_response ?: ""

        when (result.action) {
            "TAP" -> {
                val success = DeviceAutomator.tapOnText(target)
                speak(if (success) spoken else "Couldn't tap $target")
            }
            "TYPE" -> {
                DeviceAutomator.typeText(payload)
                speak(spoken)
            }
            "SCROLL" -> {
                DeviceAutomator.scrollDown()
                speak(spoken)
            }
            "ANSWER" -> {
                speak(spoken)
            }
            else -> speak(spoken)
        }
        SystemDatabase(context).logCommand(result.toString(), spoken)
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "latos_${System.currentTimeMillis()}")
    }
}
