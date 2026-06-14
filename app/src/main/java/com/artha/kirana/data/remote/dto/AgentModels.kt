package com.artha.kirana.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

/** OpenAI-compatible chat message that can carry tool calls (assistant) or a tool result (tool role). */
@Serializable
data class AgentMessage(
    val role: String,                                  // "system" | "user" | "assistant" | "tool"
    val content: String? = null,                       // text, tool result, or null on an assistant tool-call turn
    @SerialName("tool_calls") val toolCalls: List<AgentToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null, // set on a "tool" result message
)

@Serializable
data class AgentToolCall(
    val id: String,
    val type: String = "function",
    val function: AgentFunctionCall,
)

@Serializable
data class AgentFunctionCall(
    val name: String,
    val arguments: String,                             // JSON object as a STRING (OpenAI format)
)

@Serializable
data class AgentRequest(
    val model: String,
    val messages: List<AgentMessage>,
    val tools: JsonArray? = null,
    @SerialName("tool_choice") val toolChoice: String? = "auto",
)

@Serializable
data class AgentResponse(
    val choices: List<AgentChoice>,
)

@Serializable
data class AgentChoice(
    val message: AgentMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)
