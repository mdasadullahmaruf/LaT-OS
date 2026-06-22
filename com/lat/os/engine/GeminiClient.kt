// com/lat/os/engine/GeminiClient.kt
package com.lat.os.engine

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

data class GeminiResponse(
    val action: String?,
    val target_text: String?,
    val payload: String?,
    val spoken_response: String?
)

object GeminiClient {
    private val client = OkHttpClient()
    private val gson = Gson()
    private val conversationHistory = mutableListOf<Map<String, Any>>()

    suspend fun askGemini(context: Context, userQuery: String, screenContext: String): GeminiResponse? {
        val apiKey = context.getSharedPreferences("latos_prefs", Context.MODE_PRIVATE)
            .getString("gemini_api_key", "") ?: return null
        if (apiKey.isBlank()) return null

        // Build prompt with screen context
        val systemPrompt = """
You are an automation assistant on Android. Your job is to convert the user's spoken command into a precise screen interaction.
Current screen elements (simplified):
$screenContext

Respond ONLY with a JSON object in this exact format, no additional text:
{
  "action": "TAP | TYPE | SCROLL | ANSWER",
  "target_text": "string_to_find",
  "payload": "text_to_type_if_applicable",
  "spoken_response": "What to say to the user"
}
""".trimIndent()

        conversationHistory.add(mapOf("role" to "user", "parts" to listOf(mapOf("text" to userQuery))))
        conversationHistory.add(mapOf("role" to "model", "parts" to listOf(mapOf("text" to "")))) // placeholder

        val contents = JSONArray()
        conversationHistory.forEach { msg ->
            val obj = JSONObject()
            obj.put("role", msg["role"])
            obj.put("parts", JSONArray((msg["parts"] as List<*>)))
            contents.put(obj)
        }

        val requestBody = JSONObject().apply {
            put("contents", contents)
            put("systemInstruction", JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("text", systemPrompt) })
                })
            })
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash-exp:generateContent?key=$apiKey")
            .post(RequestBody.create("application/json".toMediaType(), requestBody.toString()))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val candidates = json.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    val text = parts.getJSONObject(0).getString("text")
                    // Extract JSON from response (may contain markdown fences)
                    val cleanJson = text.replace("```json", "").replace("```", "").trim()
                    gson.fromJson(cleanJson, GeminiResponse::class.java)
                } else null
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}
