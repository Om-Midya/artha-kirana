package com.artha.kirana.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** OpenAI-compatible request for OpenRouter chat-completions (text path). */
@Serializable
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    val temperature: Double = 0.0,
    @SerialName("response_format") val responseFormat: JsonObject? = null,
)

@Serializable
data class OpenRouterMessage(
    val role: String,
    val content: String,
)

/**
 * Pure builder for the text request. The local path passes a llama.cpp json_schema envelope;
 * OpenRouter/Claude can't take that, so any non-null [responseFormat] is collapsed to
 * `{"type":"json_object"}` and we rely on the system prompt + JsonParser.extractJson. A null
 * [responseFormat] omits the field entirely.
 */
fun buildOpenRouterTextRequest(
    model: String,
    system: String,
    user: String,
    responseFormat: JsonElement?,
): OpenRouterRequest = OpenRouterRequest(
    model = model,
    messages = listOf(
        OpenRouterMessage("system", system),
        OpenRouterMessage("user", user),
    ),
    responseFormat = if (responseFormat != null) {
        JsonObject(mapOf("type" to JsonPrimitive("json_object")))
    } else {
        null
    },
)
