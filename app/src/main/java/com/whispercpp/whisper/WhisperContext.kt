package com.whispercpp.whisper

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Owns one native whisper context. All native calls run on a single dedicated thread
 * (whisper is not reentrant; the model is loaded once and reused).
 */
class WhisperContext private constructor(private var ptr: Long) {

    private val dispatcher = Executors.newSingleThreadExecutor { Thread(it, "whisper") }
        .asCoroutineDispatcher()
    private val scope = CoroutineScope(dispatcher)

    /** Transcribe 16 kHz mono float samples (range -1..1). Returns the joined text. */
    suspend fun transcribe(audio: FloatArray, lang: String = "hi"): String =
        withContext(scope.coroutineContext) {
            check(ptr != 0L) { "Whisper context already released" }
            val threads = (Runtime.getRuntime().availableProcessors() - 1).coerceIn(1, 6)
            WhisperLib.fullTranscribe(ptr, threads, lang, audio)
            val count = WhisperLib.getTextSegmentCount(ptr)
            buildString {
                for (i in 0 until count) {
                    // Decode raw bytes as UTF-8 (tolerates whisper's stray token-boundary bytes
                    // instead of crashing in NewStringUTF), then drop any replacement chars.
                    append(String(WhisperLib.getTextSegment(ptr, i), Charsets.UTF_8))
                }
            }.replace("�", "").trim()
        }

    suspend fun release() = withContext(scope.coroutineContext) {
        if (ptr != 0L) {
            WhisperLib.freeContext(ptr)
            ptr = 0L
        }
    }

    companion object {
        /** Loads a ggml model from an absolute filesystem path (e.g. /sdcard/Download/ggml-tiny.bin). */
        fun createFromFile(modelPath: String): WhisperContext {
            val ptr = WhisperLib.initContext(modelPath)
            check(ptr != 0L) { "Failed to load whisper model at $modelPath" }
            return WhisperContext(ptr)
        }

        fun systemInfo(): String = WhisperLib.getSystemInfo()
    }
}
