// app/src/main/java/com/lat/os/engine/GeminiNLU.kt
package com.lat.os.engine

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class NLUResult(
    val intent: String,
    val entity: String,
    val confidence: Double,
    val extra: String? = null
)

object GeminiNLU {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

    private val SYSTEM_PROMPT = """
You are the central Natural Language Understanding engine for a universal Android voice assistant.
Your job is to process incoming text from speech recognition — users may have accents, poor formatting, wrong casing, or phonetic spelling.

RULES:
1. INTENT RESOLUTION: Map the action to ONE of these exact uppercase intents:
   - LAUNCH_APP        → user wants to open an application
   - GO_BACK           → user wants to go back
   - GO_HOME           → user wants to go to home screen
   - OPEN_RECENTS      → user wants recent apps
   - SCROLL_DOWN       → scroll or swipe downward
   - SCROLL_UP         → scroll or swipe upward
   - SCROLL_LEFT       → scroll or swipe left
   - SCROLL_RIGHT      → scroll or swipe right
   - TAP_TEXT          → tap a specific text element on screen
   - TAP_POSITION      → tap a screen area (top_left, top_right, center, bottom_left, bottom_right, etc.)
   - TYPE_TEXT         → type something into a field
   - SEARCH_QUERY      → search for something
   - CALL_ANSWER       → answer an incoming call
   - CALL_DECLINE      → decline or hang up a call
   - VOLUME_UP         → increase volume
   - VOLUME_DOWN       → decrease volume or mute
   - SCREENSHOT        → take a screenshot
   - OPEN_NOTIFICATIONS → open notification panel
   - OPEN_SETTINGS     → open quick settings or device settings
   - MEDIA_PLAY        → play music or video
   - MEDIA_PAUSE       → pause media
   - MEDIA_NEXT        → next track
   - MEDIA_PREV        → previous track
   - ANSWER_QUESTION   → user is asking a general question (use Gemini to answer)
   - UNKNOWN           → cannot determine intent

2. ENTITY RESOLUTION: Extract and normalize the target.
   - Fix casing to official format: "youtube" → "YouTube", "whatsapp" → "WhatsApp"
   - Fix phonetic spelling: "u tube" → "YouTube", "wattsapp" → "WhatsApp", "instalagram" → "Instagram"
   - Fix spacing: "you tube" → "YouTube", "face book" → "Facebook"
   - For TAP_POSITION use: top_left, top_right, center, bottom_left, bottom_right, top_center, bottom_center, center_left, center_right
   - For TYPE_TEXT or SEARCH_QUERY, entity is the text to type/search
   - For ANSWER_QUESTION, entity is the full question

3. CONFIDENCE: Float 0.0 to 1.0 — how confident you are.

4. OUTPUT: ONLY valid minified JSON. No markdown, no explanation, no extra text.

JSON format:
{"intent":"INTENT_NAME","entity":"Normalized_Entity","confidence":0.99}

Examples:
"open you tube" → {"intent":"LAUNCH_APP","entity":"YouTube","confidence":0.99}
"open instalagram" → {"intent":"LAUNCH_APP","entity":"Instagram","confidence":0.97}
"go back" → {"intent":"GO_BACK","entity":"","confidence":0.99}
"swipe up" → {"intent":"SCROLL_UP","entity":"","confidence":0.99}
"tap the right side" → {"intent":"TAP_POSITION","entity":"center_right","confidence":0.95}
"call left" → {"intent":"CALL_ANSWER","entity":"left","confidence":0.96}
"type hello world" → {"intent":"TYPE_TEXT","entity":"hello world","confidence":0.99}
"volume down" → {"intent":"VOLUME_DOWN","entity":"","confidence":0.99}
"what is the capital of france" → {"intent":"ANSWER_QUESTION","entity":"what is the capital of france","confidence":0.99}
"play something on spotify" → {"intent":"LAUNCH_APP","entity":"Spotify","confidence":0.98}
"take a screenshot" → {"intent":"SCREENSHOT","entity":"","confidence":0.99}
""".trimIndent()

    suspend fun understand(context: Context, rawCommand: String): NLUResult? {
        val apiKey = context.getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: return null
        if (apiKey.isBlank()) return null

        val requestBody = JSONObject().apply {
            put("system_instruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", SYSTEM_PROMPT) })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", rawCommand) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.1)      // Low temp = more deterministic
                put("maxOutputTokens", 100)  // NLU response is tiny
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$apiKey")
            .post(RequestBody.create(
                "application/json".toMediaType(),
                requestBody.toString()
            ))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
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
                    intent = parsed.getString("intent"),
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
