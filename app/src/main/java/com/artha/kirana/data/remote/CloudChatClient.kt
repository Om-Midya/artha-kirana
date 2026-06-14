package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.data.remote.dto.buildOpenRouterTextRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud parser backed by OpenRouter (OpenAI-compatible) → Claude Haiku 4.5. Drop-in for the local
 * [LlmHttpClient]: same [chat] contract, but a frontier model parses our schema more reliably than
 * the on-device 3B. Throws on any failure (blank key / non-2xx / blank body) so [FallbackChatClient]
 * falls back to local. Uses a short per-request timeout so a slow/no-network cloud drops fast.
 */
@Singleton
class CloudChatClient @Inject constructor(
    private val client: HttpClient,
) {
    private val apiKey: String get() = BuildConfig.OPENROUTER_KEY
    private val model: String get() = BuildConfig.OPENROUTER_MODEL

    suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String {
        if (apiKey.isBlank()) throw LlmUnavailableException(null)
        val response = client.post("$BASE_URL/chat/completions") {
            timeout { requestTimeoutMillis = CLOUD_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://artha.kirana")
            header("X-Title", "Artha Kirana")
            setBody(buildOpenRouterTextRequest(model, system, user, responseFormat))
        }
        if (!response.status.isSuccess()) {
            throw LlmUnavailableException(RuntimeException("OpenRouter ${response.status.value}"))
        }
        val content = response.body<ChatCompletionResponse>().choices.firstOrNull()?.message?.content
        return content?.takeIf { it.isNotBlank() }
            ?: throw LlmUnavailableException(null)
    }

    /** Cheap readiness check: a key is present. Real failures surface at [chat] and fall back. */
    fun keyPresent(): Boolean = apiKey.isNotBlank()

    /**
     * One turn of an OpenAI-style tool-calling loop: send the running [messages] + [tools] and return
     * the assistant's reply (which may contain tool_calls or final content). Cloud-only; throws
     * [LlmUnavailableException] on blank key / non-2xx / blank so the caller can fall back to the
     * on-device intent router (which cannot tool-call). Generous timeout — a multi-tool answer makes
     * several round-trips inside ONE [completeWithTools] only at the model's end, but allow for slow nets.
     */
    suspend fun completeWithTools(
        messages: List<com.artha.kirana.data.remote.dto.AgentMessage>,
        tools: kotlinx.serialization.json.JsonArray,
    ): com.artha.kirana.data.remote.dto.AgentMessage {
        if (apiKey.isBlank()) throw LlmUnavailableException(null)
        val response = client.post("$BASE_URL/chat/completions") {
            timeout { requestTimeoutMillis = AGENT_TIMEOUT_MS }
            contentType(ContentType.Application.Json)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("HTTP-Referer", "https://artha.kirana")
            header("X-Title", "Artha Kirana")
            setBody(
                com.artha.kirana.data.remote.dto.AgentRequest(
                    model = model,
                    messages = messages,
                    tools = tools,
                    toolChoice = "auto",
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw LlmUnavailableException(RuntimeException("OpenRouter ${response.status.value}"))
        }
        return response.body<com.artha.kirana.data.remote.dto.AgentResponse>()
            .choices.firstOrNull()?.message
            ?: throw LlmUnavailableException(null)
    }

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        const val CLOUD_TIMEOUT_MS = 10_000L
        const val AGENT_TIMEOUT_MS = 30_000L
    }
}
