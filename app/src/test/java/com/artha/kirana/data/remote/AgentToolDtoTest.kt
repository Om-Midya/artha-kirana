package com.artha.kirana.data.remote

import com.artha.kirana.data.remote.dto.AgentMessage
import com.artha.kirana.data.remote.dto.AgentRequest
import com.artha.kirana.data.remote.dto.AgentResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolDtoTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

    @Test
    fun `request serializes tools and tool_choice`() {
        val req = AgentRequest(
            model = "m",
            messages = listOf(AgentMessage(role = "user", content = "hi")),
            tools = buildJsonArray { },
            toolChoice = "auto",
        )
        val obj = json.encodeToJsonElement(req).jsonObject
        assertTrue(obj.containsKey("tools"))
        assertEquals("\"auto\"", obj["tool_choice"].toString())
    }

    @Test
    fun `response with tool_calls parses`() {
        val raw = """
            {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
            "tool_calls":[{"id":"call_1","type":"function","function":{"name":"get_pnl","arguments":"{\"period\":\"today\"}"}}]}}]}
        """.trimIndent()
        val resp = json.decodeFromString(AgentResponse.serializer(), raw)
        val msg = resp.choices.first().message
        assertEquals("tool_calls", resp.choices.first().finishReason)
        assertNull(msg.content)
        assertEquals("get_pnl", msg.toolCalls!!.first().function.name)
        assertTrue(msg.toolCalls!!.first().function.arguments.contains("today"))
    }

    @Test
    fun `response with final text parses`() {
        val raw = """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":"आज ₹500"}}]}"""
        val resp = json.decodeFromString(AgentResponse.serializer(), raw)
        val msg = resp.choices.first().message
        assertEquals("आज ₹500", msg.content)
        assertNull(msg.toolCalls)
    }
}
