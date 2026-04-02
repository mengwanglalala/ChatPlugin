package com.chatplugin.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.chatplugin.model.AIConfig
import com.chatplugin.model.AIProvider

class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "chat_plugin_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveConfig(config: AIConfig) {
        prefs.edit()
            .putString("provider", config.provider.name)
            .putString("api_key", config.apiKey)
            .putString("model", config.model)
            .putString("base_url", config.baseUrl)
            .putString("system_prompt", config.systemPrompt)
            .putInt("max_suggestions", config.maxSuggestions)
            .putInt("context_messages", config.contextMessages)
            .apply()
    }

    fun loadConfig(): AIConfig = AIConfig(
        provider = AIProvider.valueOf(
            prefs.getString("provider", AIProvider.OPENAI_COMPATIBLE.name)!!
        ),
        apiKey = prefs.getString("api_key", "") ?: "",
        model = prefs.getString("model", "deepseek-chat") ?: "deepseek-chat",
        baseUrl = prefs.getString("base_url", "https://api.deepseek.com/v1/") ?: "",
        systemPrompt = prefs.getString("system_prompt", AIConfig.DEFAULT_SYSTEM_PROMPT)
            ?: AIConfig.DEFAULT_SYSTEM_PROMPT,
        maxSuggestions = prefs.getInt("max_suggestions", 3),
        contextMessages = prefs.getInt("context_messages", 10)
    )
}
