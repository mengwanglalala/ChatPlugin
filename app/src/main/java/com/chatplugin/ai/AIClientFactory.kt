package com.chatplugin.ai

import com.chatplugin.model.AIConfig
import com.chatplugin.model.AIProvider

object AIClientFactory {
    fun create(config: AIConfig): AIClient = when (config.provider) {
        AIProvider.CLAUDE -> ClaudeClient(config)
        AIProvider.OPENAI_COMPATIBLE -> OpenAICompatibleClient(config)
    }
}
