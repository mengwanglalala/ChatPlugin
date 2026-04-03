package com.chatplugin.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.chatplugin.model.Message

/** 轻量数据类，便于单元测试中构造模拟数据 */
data class RawNode(val text: String, val centerX: Int)

object MessageExtractor {

    /**
     * 从 RawNode 列表中提取消息，通过 centerX 与屏幕中线的相对位置判断发送方。
     * 保留顺序（从旧到新），取最后 maxCount 条。
     */
    fun extract(nodes: List<RawNode>, screenWidth: Int, maxCount: Int): List<Message> {
        val midX = screenWidth / 2
        return nodes
            .filter { it.text.isNotBlank() }
            .map { node ->
                Message(
                    text = node.text.trim(),
                    isFromMe = node.centerX > midX
                )
            }
            .takeLast(maxCount)
    }

    /**
     * 从 AccessibilityNodeInfo 根节点提取 RawNode 列表。
     * 递归遍历，收集所有有文本内容的叶子节点。
     */
    fun extractFromNode(root: AccessibilityNodeInfo, screenWidth: Int, maxCount: Int): List<Message> {
        val rawNodes = mutableListOf<RawNode>()
        collectTextNodes(root, rawNodes)
        return extract(rawNodes, screenWidth, maxCount)
    }

    private fun collectTextNodes(node: AccessibilityNodeInfo, result: MutableList<RawNode>) {
        val text = node.text?.toString() ?: node.contentDescription?.toString()
        if (!text.isNullOrBlank() && node.childCount == 0) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            result.add(RawNode(text = text, centerX = bounds.centerX()))
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTextNodes(it, result) }
        }
    }
}
