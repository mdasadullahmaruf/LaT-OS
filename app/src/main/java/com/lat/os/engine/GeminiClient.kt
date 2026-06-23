// app/src/main/java/com/lat/os/engine/GeminiClient.kt
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

data class GeminiResponse(
    val action: String?,
    val target_text: String?,
    val payload: String?,
    val spoken_response: String?
)

object GeminiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun askGemini(
        context: Context,
        userQuery: String,
        screenContext: String
    ): GeminiResponse? {
        val apiKey = context
            .getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: return null

        if (apiKey.isBlank()) return null

        val systemPrompt = """
You are an Android automation assistant.
Current screen content:
$screenContext

User command: $userQuery

Respond with ONLY a JSON object:
{"action":"TAP|TYPE|SCROLL|ANSWER","target_text":"text","payload":"value","spoken_response":"what to say"}
        """.trimIndent()

        return withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", systemPrompt)
                                })
                            })
                        })
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
                GeminiResponse(
                    action = parsed.optString("action"),
                    target_text = parsed.optString("target_text"),
                    payload = parsed.optString("payload"),
                    spoken_response = parsed.optString("spoken_response")
                )
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
