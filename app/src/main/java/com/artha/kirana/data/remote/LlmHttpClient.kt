package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import com.artha.kirana.data.remote.dto.ChatCompletionRequest
import com.artha.kirana.data.remote.dto.ChatCompletionResponse
import com.artha.kirana.data.remote.dto.ChatMessage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/** Thrown when the on-device llama-server cannot be reached or returns nothing usable. */
class LlmUnavailableException(cause: Throwable? = null) :
    Exception("On-device LLM is unreachable. Start it with scripts/start-llama-server.sh", cause)

/**
 * Ktor client for the on-device LLM (llama-server, OpenAI-compatible) on loopback.
 * Both this and [ClaudeApiClient] (Phase 5) share the Hilt-provided HttpClient.
 */
@Singleton
class LlmHttpClient @Inject constructor(
    private val client: HttpClient,
) {
    private val baseUrl: String = BuildConfig.LLM_BASE_URL

    /** True when the server answers /health with 2xx. Used for graceful degradation. */
    suspend fun health(): Boolean = try {
        client.get("$baseUrl/health").status.isSuccess()
    } catch (t: Throwable) {
        false
    }

    /** Sends a system+user turn and returns the assistant content, or throws [LlmUnavailableException]. */
    suspend fun chat(system: String, user: String): String {
        val response: ChatCompletionResponse = try {
            client.post("$baseUrl/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                setBody(
                    ChatCompletionRequest(
                        messages = listOf(
                            ChatMessage(role = "system", content = system),
                            ChatMessage(role = "user", content = user),
                        ),
                    ),
                )
            }.body()
        } catch (t: Throwable) {
            throw LlmUnavailableException(t)
        }
        return response.choices.firstOrNull()?.message?.content
            ?: throw LlmUnavailableException(null)
    }
}
