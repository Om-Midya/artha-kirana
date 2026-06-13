package com.artha.kirana.data.voice

import com.artha.kirana.BuildConfig
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device speech-to-text via whisper.cpp (JNI). The ggml model is loaded once, lazily, and
 * kept in memory (singleton). The model is adb-pushed to [MODEL_PATH] — same on-device pattern
 * as the LLM (.gguf) — so nothing leaves the phone.
 *
 * NOTE (CLAUDE.md §16): never run whisper and the LLM at the same time — sequence transcription
 * BEFORE the parse call. The mutex here only serializes whisper calls among themselves.
 */
@Singleton
class WhisperEngine @Inject constructor() {

    companion object {
        const val MODEL_PATH = "/sdcard/Download/ggml-tiny.bin"
    }

    private val mutex = Mutex()
    @Volatile private var context: WhisperContext? = null

    fun isModelPresent(): Boolean = File(MODEL_PATH).exists()

    val voiceEnabled: Boolean get() = BuildConfig.VOICE_ENABLED

    /** Transcribe normalized 16 kHz mono samples to text. Loads the model on first call. */
    suspend fun transcribe(audio: FloatArray, lang: String = "hi"): String = mutex.withLock {
        val ctx = context ?: WhisperContext.createFromFile(MODEL_PATH).also {
            context = it
            Timber.i("Whisper model loaded from %s", MODEL_PATH)
        }
        ctx.transcribe(audio, lang)
    }
}
