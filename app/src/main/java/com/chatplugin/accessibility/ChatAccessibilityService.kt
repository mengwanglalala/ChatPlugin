package com.chatplugin.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.chatplugin.ai.AIClientFactory
import com.chatplugin.overlay.OverlayManager
import com.chatplugin.storage.SecurePreferences
import com.chatplugin.model.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.withContext

class ChatAccessibilityService : AccessibilityService() {

    private lateinit var overlayManager: OverlayManager
    private lateinit var prefs: SecurePreferences
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val chatAppKeywords = listOf(
        "wechat", "weixin", "whatsapp", "telegram", "line",
        "messenger", "snapchat", "viber", "signal", "dingtalk",
        "feishu", "lark", "qq", "tim",
        "chrome" // 临时：用于模拟器测试
    )

    private var lastMessages = emptyList<Message>()
    private var contentChangedJob: Job? = null
    private var aiJob: Job? = null  // 独立保护 AI 请求，不被 contentChangedJob cancel 影响
    private var cachedKeyboardHeight = 0

    override fun onServiceConnected() {
        Log.d("ChatPlugin", "onServiceConnected")
        overlayManager = OverlayManager(this)
        prefs = SecurePreferences(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        // 输入法自身的事件不影响 overlay，直接跳过
        if (packageName.contains("inputmethod") || packageName.contains("ime")) return
        val isChatApp = chatAppKeywords.any { packageName.lowercase().contains(it) }
        Log.d("ChatPlugin", "event pkg=$packageName type=${event.eventType} isChatApp=$isChatApp")
        if (!isChatApp) {
            overlayManager.dismiss()
            return
        }

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                val node = event.source ?: return
                Log.d("ChatPlugin", "focused className=${node.className} isEditable=${node.isEditable}")
                // 兼容标准 EditText 和飞书/钉钉等自定义富文本输入框
                val cls = node.className?.toString() ?: ""
                val isInput = node.isEditable ||
                    cls.contains("EditText") ||
                    cls.contains("RichText") ||
                    cls.contains("Input")
                if (isInput) triggerSuggestions()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                contentChangedJob?.cancel()
                contentChangedJob = serviceScope.launch {
                    delay(300)
                    val newMessages = extractMessages()
                    if (newMessages != lastMessages) {
                        lastMessages = newMessages
                        generateSuggestions(newMessages)
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val kbHeight = getKeyboardHeight()
                if (kbHeight > 0) {
                    overlayManager.updateKeyboardHeight(kbHeight)
                    Log.d("ChatPlugin", "keyboard appeared, height=$kbHeight")
                } else if (cachedKeyboardHeight > 0) {
                    Log.d("ChatPlugin", "keyboard dismissed (was $cachedKeyboardHeight)")
                    cachedKeyboardHeight = 0
                    overlayManager.dismiss()
                }
            }
        }
    }

    private fun triggerSuggestions() {
        serviceScope.launch {
            delay(400) // 等键盘弹出后再触发
            val messages = extractMessages()
            lastMessages = messages
            generateSuggestions(messages)
        }
    }

    private suspend fun extractMessages(): List<Message> = withContext(Dispatchers.IO) {
        val root = rootInActiveWindow ?: return@withContext emptyList()
        val metrics = resources.displayMetrics
        val config = prefs.loadConfig()
        MessageExtractor.extractFromNode(root, metrics.widthPixels, config.contextMessages)
    }

    private fun getKeyboardHeight(): Int {
        val screenHeight = resources.displayMetrics.heightPixels
        windows?.forEach { window ->
            if (window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                val bounds = Rect()
                window.getBoundsInScreen(bounds)
                val height = screenHeight - bounds.top
                if (height > 100) {
                    cachedKeyboardHeight = height
                    Log.d("ChatPlugin", "keyboard height=$height")
                }
                return height
            }
        }
        return 0
    }

    private fun generateSuggestions(messages: List<Message>) {
        Log.d("ChatPlugin", "generateSuggestions messages=${messages.size}")
        if (messages.isEmpty()) return
        val config = prefs.loadConfig()

        val kbHeightNow = getKeyboardHeight().takeIf { it > 0 } ?: cachedKeyboardHeight
        Log.d("ChatPlugin", "kbHeight at generateSuggestions start=$kbHeightNow")
        if (kbHeightNow > 0) {
            overlayManager.updateKeyboardHeight(kbHeightNow)
            overlayManager.showLoading()
        }

        // 取消上一次 AI 请求，避免重复调用
        aiJob?.cancel()
        // 用独立 Job 保护 AI 请求，不被 contentChangedJob 的 cancel 影响
        aiJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                Log.d("ChatPlugin", "API call start, provider=${config.provider}, model=${config.model}")
                val client = AIClientFactory.create(config)
                val suggestions = client.getSuggestions(messages, config.maxSuggestions)
                Log.d("ChatPlugin", "API success: $suggestions")
                withContext(Dispatchers.Main) {
                    val kbHeight = getKeyboardHeight().takeIf { it > 0 } ?: cachedKeyboardHeight
                    Log.d("ChatPlugin", "keyboard height=$kbHeight before show")
                    if (kbHeight > 0) overlayManager.updateKeyboardHeight(kbHeight)
                    overlayManager.show(suggestions) { selected -> fillInput(selected) }
                }
            } catch (e: CancellationException) {
                Log.d("ChatPlugin", "AI job cancelled (new request incoming)")
            } catch (e: Exception) {
                Log.e("ChatPlugin", "API error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    overlayManager.showError { generateSuggestions(messages) }
                }
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
        contentChangedJob?.cancel()
        overlayManager.dismiss()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        overlayManager.dismiss()
        super.onDestroy()
    }
}
