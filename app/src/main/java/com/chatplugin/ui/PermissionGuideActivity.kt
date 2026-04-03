package com.chatplugin.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class PermissionGuideActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                PermissionGuideScreen(
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionGuideScreen(onOpenSettings: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("聊天插件", style = MaterialTheme.typography.headlineLarge)
        Text(
            "在任意聊天 App 中，AI 自动读取最近消息并在键盘上方生成回复建议。",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(Modifier.height(8.dp))

        Text("需要开启两项权限：", style = MaterialTheme.typography.titleMedium)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 无障碍服务", style = MaterialTheme.typography.titleSmall)
                Text("用于读取聊天内容，生成建议后写入输入框。不存储任何数据。")
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("去开启无障碍服务") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2. 显示在其他应用上层", style = MaterialTheme.typography.titleSmall)
                Text("用于在键盘上方显示建议条。")
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"))
                    )
                }) { Text("去开启悬浮窗权限") }
            }
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onOpenSettings,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("权限已开启，进入设置")
        }
    }
}
