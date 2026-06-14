package com.artha.kirana.data.remote

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.JsonElement

/** Which backend answered the most recent [ChatClient.chat] call. Surfaced as a UI badge. */
enum class LlmEngineKind { CLOUD, ON_DEVICE, NONE }

/**
 * The single LLM chokepoint. [IntentRouter]/[LlmEngine] depend on this, not on a concrete client,
 * so the app can be cloud-primary with a local fallback by swapping the bound implementation.
 */
interface ChatClient {
    /** Sends a system+user turn; returns the assistant content or throws [LlmUnavailableException]. */
    suspend fun chat(system: String, user: String, responseFormat: JsonElement? = null): String

    /** True when at least one backend is plausibly reachable (used for graceful degradation). */
    suspend fun health(): Boolean

    /** The backend that answered the most recent [chat]. Read by the engine badge. */
    val engine: StateFlow<LlmEngineKind>
}
