package com.artha.kirana.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OpenAI-compatible chat-completions DTOs for the on-device llama-server.
 * Shape verified against SPIKE A (build b9620): the response carries many extra
 * fields (created, model, usage, timings, ...) so the JSON parser must ignore unknown keys.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
data class ChatCompletionRequest(
    val messages: List<ChatMessage>,
    val temperature: Double = 0.1,
    @SerialName("max_tokens") val maxTokens: Int = 256,
    val stop: List<String> = listOf("```"),
)

@Serializable
data class ChatChoice(
    val message: ChatMessage,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice>,
)
