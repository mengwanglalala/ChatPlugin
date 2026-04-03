package com.chatplugin.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chatplugin.model.AIConfig
import com.chatplugin.model.AIProvider
import com.chatplugin.storage.SecurePreferences

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SettingsScreen(SecurePreferences(this))
            }
        }
    }
}

@Composable
fun SettingsScreen(prefs: SecurePreferences) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(prefs.loadConfig()) }

    fun save(newConfig: AIConfig) {
        config = newConfig
        prefs.saveConfig(newConfig)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("聊天插件设置", style = MaterialTheme.typography.headlineMedium)

        SectionCard(title = "模型配置") {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = if (config.provider == AIProvider.CLAUDE) "Claude" else "OpenAI 兼容（DeepSeek 等）",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("服务商") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        text = { Text("OpenAI 兼容（DeepSeek 等）") },
                        onClick = {
                            save(config.copy(provider = AIProvider.OPENAI_COMPATIBLE))
                            expanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Claude") },
                        onClick = {
                            save(config.copy(provider = AIProvider.CLAUDE))
                            expanded = false
                        }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = config.model,
                onValueChange = { save(config.copy(model = it)) },
                label = { Text("模型名称") },
                placeholder = { Text("deepseek-chat") },
                modifier = Modifier.fillMaxWidth()
            )

            if (config.provider == AIProvider.OPENAI_COMPATIBLE) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = config.baseUrl,
                    onValueChange = { save(config.copy(baseUrl = it)) },
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.deepseek.com/v1/") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = config.apiKey,
                onValueChange = { save(config.copy(apiKey = it)) },
                label = { Text("API Key") },
                placeholder = { Text("sk-...") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard(title = "上下文") {
            Text("读取消息条数 N：${config.contextMessages}")
            Slider(
                value = config.contextMessages.toFloat(),
                onValueChange = { save(config.copy(contextMessages = it.toInt())) },
                valueRange = 3f..30f,
                steps = 26
            )
            Spacer(Modifier.height(8.dp))
            Text("建议条数：${config.maxSuggestions}")
            Slider(
                value = config.maxSuggestions.toFloat(),
                onValueChange = { save(config.copy(maxSuggestions = it.toInt())) },
                valueRange = 1f..5f,
                steps = 3
            )
        }

        SectionCard(title = "权限") {
            PermissionRow(
                name = "无障碍服务",
                granted = Settings.Secure.getString(
                    context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                )?.contains("com.chatplugin") == true,
                onGrant = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
            Spacer(Modifier.height(8.dp))
            PermissionRow(
                name = "显示在其他应用上层",
                granted = Settings.canDrawOverlays(context),
                onGrant = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:com.chatplugin"))
                    )
                }
            )
        }
    }
}

@Composable
fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
fun PermissionRow(name: String, granted: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name)
        if (granted) {
            Text("已开启", color = MaterialTheme.colorScheme.primary)
        } else {
            Button(onClick = onGrant) { Text("去开启") }
        }
    }
}
