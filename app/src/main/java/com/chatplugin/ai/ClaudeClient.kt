package com.chatplugin.ai

import com.chatplugin.model.AIConfig
import com.chatplugin.model.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class ClaudeClient(
    private val config: AIConfig,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : AIClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getSuggestions(messages: List<Message>, count: Int): List<String> =
        withContext(Dispatchers.IO) {
            val systemPrompt = config.systemPrompt.replace("{n}", count.toString())

            val chatMessages = buildJsonArray {
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.isFromMe) "assistant" else "user")
                        put("content", msg.text)
                    }
                }
            }

            val body = buildJsonObject {
                put("model", config.model)
                put("system", systemPrompt)
                put("messages", chatMessages)
                put("max_tokens", 256)
            }.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", config.apiKey)
                .header("anthropic-version", "2023-06-01")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw Exception("Claude API error ${response.code}: $errorBody")
            }

            val responseBody = response.body?.use { it.string() } ?: throw Exception("Empty response")
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val contentArray = jsonResponse["content"]?.jsonArray
                ?: throw Exception("Invalid response: missing 'content'")
            if (contentArray.isEmpty()) throw Exception("Invalid response: empty 'content'")
            val content = contentArray[0].jsonObject["text"]
                ?.jsonPrimitive?.content
                ?: throw Exception("Invalid response: missing 'text'")

            content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(count)
        }
}
