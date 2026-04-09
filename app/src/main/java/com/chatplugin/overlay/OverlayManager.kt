package com.chatplugin.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var keyboardHeight = 0
    private var onSuggestionSelected: ((String) -> Unit)? = null

    private val layoutParams get() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        // FLAG_LAYOUT_IN_SCREEN: 确保坐标基于屏幕而非窗口，避免 y 偏移错误
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM
        y = keyboardHeight
        // 阻止 overlay 出现时收起键盘
        softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
    }

    fun show(suggestions: List<String>, onSelected: (String) -> Unit) {
        // 键盘未弹起时不显示
        if (keyboardHeight <= 0) return
        onSuggestionSelected = onSelected
        dismiss()
        val view = buildSuggestionBar(suggestions)
        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {
            // 权限未授权时静默失败
        }
    }

    fun showLoading() {
        // 键盘未弹起时不显示（避免黑色横杠出现在屏幕顶部）
        if (keyboardHeight <= 0) return
        dismiss()
        val view = buildLoadingBar()
        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {}
    }

    fun showError(onRetry: () -> Unit) {
        // 键盘未弹起时不显示，避免黑色横杠
        if (keyboardHeight <= 0) return
        dismiss()
        val view = buildErrorBar(onRetry)
        try {
            windowManager.addView(view, layoutParams)
            overlayView = view
        } catch (e: Exception) {}
    }

    fun dismiss() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (e: Exception) {}
            overlayView = null
        }
    }

    fun updateKeyboardHeight(height: Int) {
        keyboardHeight = height
        if (height <= 0) {
            // 键盘收起，立即隐藏所有 overlay
            dismiss()
            return
        }
        overlayView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            params.y = height
            try { windowManager.updateViewLayout(view, params) } catch (e: Exception) {}
        }
    }

    private fun buildSuggestionBar(suggestions: List<String>): LinearLayout {
        val bar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
            setBackgroundColor(0xFF1E1E2E.toInt())
        }
        suggestions.forEachIndexed { index, suggestion ->
            val chip = TextView(context).apply {
                text = suggestion
                setPadding(24, 10, 24, 10)
                setTextColor(0xFFFFFFFF.toInt())
                textSize = 13f
                setOnClickListener { onSuggestionSelected?.invoke(suggestion) }
            }
            if (index == 0) {
                chip.setBackgroundColor(0xFF6200EE.toInt())
            } else {
                chip.setBackgroundColor(0xFF2C2C2C.toInt())
            }
            bar.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8 })
        }
        return bar
    }

    private fun buildLoadingBar(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFF1E1E2E.toInt())
            addView(TextView(context).apply {
                text = "✨ 生成中..."
                setTextColor(0xFF888888.toInt())
                textSize = 13f
            })
        }
    }

    private fun buildErrorBar(onRetry: () -> Unit): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xFF1E1E2E.toInt())
            addView(TextView(context).apply {
                text = "生成失败，点击重试"
                setTextColor(0xFFFF6B6B.toInt())
                textSize = 13f
                setOnClickListener { onRetry() }
            })
        }
    }
}
