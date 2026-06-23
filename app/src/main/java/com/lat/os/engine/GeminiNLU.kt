// app/src/main/java/com/lat/os/engine/GeminiNLU.kt
package com.lat.os.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NLUResult(
    val intent: String,
    val entity: String,
    val confidence: Double
)

object GeminiNLU {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val SYSTEM_PROMPT = """
You are a voice command interpreter for an Android assistant app.
Convert the user's spoken command into a JSON object.

INTENTS you must use:
LAUNCH_APP, GO_BACK, GO_HOME, OPEN_RECENTS,
SCROLL_DOWN, SCROLL_UP, SCROLL_LEFT, SCROLL_RIGHT,
TAP_TEXT, TAP_POSITION, TYPE_TEXT, SEARCH_QUERY,
CALL_ANSWER, CALL_DECLINE, VOLUME_UP, VOLUME_DOWN,
SCREENSHOT, OPEN_NOTIFICATIONS, OPEN_SETTINGS,
MEDIA_PLAY, MEDIA_PAUSE, MEDIA_NEXT, MEDIA_PREV,
ANSWER_QUESTION, UNKNOWN

RULES:
- Fix spelling: "youtub" -> "YouTube", "wattsapp" -> "WhatsApp"
- Fix casing: "youtube" -> "YouTube"
- Fix phonetics: "u tube" -> "YouTube", "instalagram" -> "Instagram"
- Output ONLY raw JSON, no markdown, no explanation

Format:
{"intent":"INTENT_NAME","entity":"value","confidence":0.99}

Examples:
"open youtube" -> {"intent":"LAUNCH_APP","entity":"YouTube","confidence":0.99}
"open instalagram" -> {"intent":"LAUNCH_APP","entity":"Instagram","confidence":0.97}
"go back" -> {"intent":"GO_BACK","entity":"","confidence":0.99}
"scroll down" -> {"intent":"SCROLL_DOWN","entity":"","confidence":0.99}
"what is the weather" -> {"intent":"ANSWER_QUESTION","entity":"what is the weather","confidence":0.99}
"take screenshot" -> {"intent":"SCREENSHOT","entity":"","confidence":0.99}
"volume up" -> {"intent":"VOLUME_UP","entity":"","confidence":0.99}
"tap center" -> {"intent":"TAP_POSITION","entity":"center","confidence":0.99}
    """.trimIndent()

    suspend fun understand(context: Context, command: String): NLUResult? {
        val apiKey = context
            .getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: return null

        if (apiKey.isBlank()) return null

        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", SYSTEM_PROMPT)
                            })
                        })
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", command)
                                })
                            })
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.1)
                        put("maxOutputTokens", 100)
                    })
                }.toString()

                val request = Request.Builder()
                    .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$apiKey")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: return@withContext null

                val json = JSONObject(responseBody)
                val text = json
                    .getJSONArray("candidates")
                    .getJSONObject(0)
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .getJSONObject(0)
                    .getString("text")
                    .trim()
                    .replace("```json", "")
                    .replace("```", "")
                    .trim()

                val parsed = JSONObject(text)
                NLUResult(
                    intent = parsed.optString("intent", "UNKNOWN"),
                    entity = parsed.optString("entity", ""),
                    confidence = parsed.optDouble("confidence", 0.0)
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
