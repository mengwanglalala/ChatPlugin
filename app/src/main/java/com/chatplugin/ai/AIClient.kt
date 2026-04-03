package com.chatplugin.ai

import com.chatplugin.model.Message

interface AIClient {
    suspend fun getSuggestions(messages: List<Message>, count: Int): List<String>
}
