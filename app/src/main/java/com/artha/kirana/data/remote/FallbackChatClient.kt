package com.artha.kirana.data.remote

import com.artha.kirana.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud-primary [ChatClient] with the on-device llama-server as fallback. Tries [cloud] first; on
 * ANY throwable (timeout / blank key / HTTP error / blank body) it falls back to [local]. If both
 * fail it re-throws [LlmUnavailableException] so callers keep their existing manual-entry path.
 * Publishes which backend answered via [engine] for the UI badge. The debug FORCE_LOCAL_LLM flag
 * short-circuits to local for demoing the offline story.
 */
@Singleton
class FallbackChatClient @Inject constructor(
    private val cloud: CloudChatClient,
    private val local: LlmHttpClient,
) : ChatClient {

    private val _engine = MutableStateFlow(LlmEngineKind.NONE)
    override val engine = _engine.asStateFlow()

    override suspend fun chat(system: String, user: String, responseFormat: JsonElement?): String {
        if (!BuildConfig.FORCE_LOCAL_LLM) {
            try {
                val out = cloud.chat(system, user, responseFormat)
                _engine.value = LlmEngineKind.CLOUD
                return out
            } catch (t: Throwable) {
                Timber.w(t, "Cloud LLM failed, falling back to on-device")
            }
        }
        return try {
            val out = local.chat(system, user, responseFormat)
            _engine.value = LlmEngineKind.ON_DEVICE
            out
        } catch (e: LlmUnavailableException) {
            _engine.value = LlmEngineKind.NONE
            throw e
        }
    }

    override suspend fun health(): Boolean =
        (!BuildConfig.FORCE_LOCAL_LLM && cloud.keyPresent()) || local.health()
}
