package com.chatplugin.model

enum class AIProvider { CLAUDE, OPENAI_COMPATIBLE }

data class AIConfig(
    val provider: AIProvider = AIProvider.OPENAI_COMPATIBLE,
    val apiKey: String = "",
    val model: String = "deepseek-chat",
    val baseUrl: String = "https://api.deepseek.com/v1/",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val maxSuggestions: Int = 3,
    val contextMessages: Int = 10
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "根据以下对话上下文，生成{n}条简短口语化中文回复建议，每条一行，不加编号，不加引号"
    }
}
