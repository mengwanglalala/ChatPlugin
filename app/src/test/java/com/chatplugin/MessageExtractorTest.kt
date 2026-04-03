package com.chatplugin

import com.chatplugin.accessibility.MessageExtractor
import com.chatplugin.accessibility.RawNode
import kotlin.test.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageExtractorTest {

    @Test fun `extracts messages and identifies sender by position`() {
        val nodes = listOf(
            RawNode(text = "今晚有空吗", centerX = 200),   // 对方（左）
            RawNode(text = "有啊", centerX = 900),          // 自己（右）
            RawNode(text = "七点老地方？", centerX = 210),  // 对方（左）
        )
        val screenWidth = 1080

        val messages = MessageExtractor.extract(nodes, screenWidth, maxCount = 10)

        assertEquals(3, messages.size)
        assertEquals("今晚有空吗", messages[0].text)
        assertEquals(false, messages[0].isFromMe)
        assertEquals("有啊", messages[1].text)
        assertEquals(true, messages[1].isFromMe)
        assertEquals("七点老地方？", messages[2].text)
        assertEquals(false, messages[2].isFromMe)
    }

    @Test fun `respects maxCount limit`() {
        val nodes = (1..20).map { i ->
            RawNode(text = "msg$i", centerX = if (i % 2 == 0) 900 else 200)
        }
        val messages = MessageExtractor.extract(nodes, screenWidth = 1080, maxCount = 5)
        assertEquals(5, messages.size)
        // 取最后 5 条（最近的）
        assertEquals("msg16", messages[0].text)
    }

    @Test fun `filters out empty text nodes`() {
        val nodes = listOf(
            RawNode(text = "", centerX = 200),
            RawNode(text = "  ", centerX = 200),
            RawNode(text = "你好", centerX = 200),
        )
        val messages = MessageExtractor.extract(nodes, screenWidth = 1080, maxCount = 10)
        assertEquals(1, messages.size)
        assertEquals("你好", messages[0].text)
    }
}
