package com.artha.kirana.data.remote

import com.artha.kirana.data.remote.dto.ChatCompletionRequest
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.data.remote.dto.ChatMessage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class LlmDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun serializesRequestWithSnakeCaseMaxTokens() {
        val req = ChatCompletionRequest(
            messages = listOf(ChatMessage("user", "hi")),
            maxTokens = 128,
        )
        val encoded = json.encodeToString(ChatCompletionRequest.serializer(), req)
        assert(encoded.contains("\"max_tokens\":128")) { "expected snake_case max_tokens in: $encoded" }
    }

    @Test
    fun deservesRealServerResponseIgnoringExtraFields() {
        // Trimmed copy of the actual SPIKE A response (extra fields must be ignored).
        val body = """
            {"choices":[{"finish_reason":"stop","index":0,
              "message":{"role":"assistant","content":"{\"entries\":[]}"}}],
             "created":1781356444,"model":"qwen2.5-3b","usage":{"total_tokens":182}}
        """.trimIndent()
        val resp = json.decodeFromString(ChatCompletionResponse.serializer(), body)
        assertEquals(1, resp.choices.size)
        assertEquals("""{"entries":[]}""", resp.choices.first().message.content)
        assertEquals("stop", resp.choices.first().finishReason)
    }
}
