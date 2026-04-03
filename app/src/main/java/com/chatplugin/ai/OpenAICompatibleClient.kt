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

class OpenAICompatibleClient(
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
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                messages.forEach { msg ->
                    addJsonObject {
                        put("role", if (msg.isFromMe) "assistant" else "user")
                        put("content", msg.text)
                    }
                }
            }

            val body = buildJsonObject {
                put("model", config.model)
                put("messages", chatMessages)
                put("max_tokens", 256)
            }.toString().toRequestBody("application/json".toMediaType())

            val baseUrl = config.baseUrl.trimEnd('/')
            val request = Request.Builder()
                .url("$baseUrl/chat/completions")
                .header("Authorization", "Bearer ${config.apiKey}")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("API error ${response.code}: ${response.body?.string()}")
            }

            val responseBody = response.body?.string() ?: throw Exception("Empty response")
            val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
            val content = jsonResponse["choices"]!!
                .jsonArray[0]
                .jsonObject["message"]!!
                .jsonObject["content"]!!
                .jsonPrimitive.content

            content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .take(count)
        }
}
