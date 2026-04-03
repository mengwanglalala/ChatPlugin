package com.chatplugin

import com.chatplugin.ai.OpenAICompatibleClient
import com.chatplugin.model.AIConfig
import com.chatplugin.model.AIProvider
import com.chatplugin.model.Message
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class OpenAICompatibleClientTest {

    private val server = MockWebServer()

    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `returns parsed suggestions from API response`() = runTest {
        server.enqueue(
            MockResponse()
                .setBody("""
                    {
                      "choices": [{
                        "message": {
                          "content": "好啊没问题！\n稍等确认下\n七点可以"
                        }
                      }]
                    }
                """.trimIndent())
                .setResponseCode(200)
        )

        val config = AIConfig(
            provider = AIProvider.OPENAI_COMPATIBLE,
            apiKey = "test-key",
            model = "deepseek-chat",
            baseUrl = server.url("/").toString()
        )
        val client = OpenAICompatibleClient(config)
        val messages = listOf(
            Message("今晚有空吗", isFromMe = false),
            Message("有啊", isFromMe = true),
            Message("七点，老地方？", isFromMe = false)
        )

        val suggestions = client.getSuggestions(messages, count = 3)

        assertEquals(3, suggestions.size)
        assertEquals("好啊没问题！", suggestions[0])
        assertEquals("稍等确认下", suggestions[1])
        assertEquals("七点可以", suggestions[2])
    }

    @Test fun `throws exception on API error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401))

        val config = AIConfig(
            provider = AIProvider.OPENAI_COMPATIBLE,
            apiKey = "bad-key",
            model = "deepseek-chat",
            baseUrl = server.url("/").toString()
        )
        val client = OpenAICompatibleClient(config)

        try {
            client.getSuggestions(listOf(Message("hi", false)), count = 3)
            assert(false) { "Should have thrown" }
        } catch (e: Exception) {
            assert(e.message?.contains("401") == true)
        }
    }
}
