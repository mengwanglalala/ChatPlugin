package com.chatplugin.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.chatplugin.ai.AIClientFactory
import com.chatplugin.overlay.OverlayManager
import com.chatplugin.storage.SecurePreferences
import com.chatplugin.model.Message
import kotlinx.coroutines.*

class ChatAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var prefs: SecurePreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val wm by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }

    private val chatAppKeywords = listOf(
        "wechat", "weixin", "whatsapp", "telegram", "line",
        "messenger", "snapchat", "viber", "signal", "dingtalk",
        "feishu", "lark", "qq", "tim"
    )

    private var lastMessages = emptyList<Message>()

    override fun onServiceConnected() {
        overlayManager = OverlayManager(this)
        prefs = SecurePreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val isChatApp = chatAppKeywords.any { packageName.lowercase().contains(it) }
        if (!isChatApp) {
            overlayManager.dismiss()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source ?: return
                if (node.className?.contains("EditText") == true) {
                    triggerSuggestions()
                }
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                serviceScope.launch {
                    delay(300)
                    val newMessages = extractMessages()
                    if (newMessages != lastMessages) {
                        lastMessages = newMessages
                        generateSuggestions(newMessages)
                    }
                }
            }
        }
    }

    private fun triggerSuggestions() {
        serviceScope.launch {
            val messages = extractMessages()
            lastMessages = messages
            generateSuggestions(messages)
        }
    }

    private fun extractMessages(): List<Message> {
        val root = rootInActiveWindow ?: return emptyList()
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)
        val config = prefs.loadConfig()
        return MessageExtractor.extractFromNode(root, metrics.widthPixels, config.contextMessages)
    }

    private fun generateSuggestions(messages: List<Message>) {
        if (messages.isEmpty()) return
        overlayManager.showLoading()
        val config = prefs.loadConfig()
        serviceScope.launch {
            try {
                val client = AIClientFactory.create(config)
                val suggestions = client.getSuggestions(messages, config.maxSuggestions)
                overlayManager.show(suggestions) { selected -> fillInput(selected) }
            } catch (e: Exception) {
                overlayManager.showError { generateSuggestions(messages) }
            }
        }
    }

    private fun fillInput(text: String) {
        val root = rootInActiveWindow ?: return fallbackCopy(text)
        val inputNode = findEditText(root) ?: return fallbackCopy(text)
        val args = android.os.Bundle()
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
            text
        )
        val success = inputNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!success) fallbackCopy(text)
    }

    private fun findEditText(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.className?.contains("EditText") == true && node.isFocused) return node
        for (i in 0 until node.childCount) {
            val found = node.getChild(i)?.let { findEditText(it) }
            if (found != null) return found
        }
        return null
    }

    private fun fallbackCopy(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("suggestion", text))
        Toast.makeText(this, "已复制，请手动粘贴", Toast.LENGTH_SHORT).show()
    }

    override fun onInterrupt() {
        overlayManager.dismiss()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayManager.dismiss()
        super.onDestroy()
    }
}
