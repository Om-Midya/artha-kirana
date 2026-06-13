package com.artha.kirana.data.voice

import android.content.Context
import com.artha.kirana.BuildConfig
import com.whispercpp.whisper.WhisperContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device speech-to-text via whisper.cpp (JNI). The ggml model is loaded once, lazily, and
 * kept in memory (singleton).
 *
 * The model lives in the app's OWN external-files dir — `getExternalFilesDir()/ggml-tiny.bin` —
 * which the sandboxed app can read without any storage permission. (Android 13+ scoped storage
 * blocks raw reads of /sdcard/Download from the app process; only the adb-shell-owned llama-server
 * can read the shared Download dir.) adb-push the model to:
 *   /sdcard/Android/data/com.artha.kirana/files/ggml-tiny.bin
 *
 * NOTE (CLAUDE.md §16): never run whisper and the LLM at the same time — sequence transcription
 * BEFORE the parse call. The mutex here only serializes whisper calls among themselves.
 */
@Singleton
class WhisperEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        // Fine-tuned Hindi whisper (vasista22/whisper-hindi-small → ggml q5_1). Far better Hindi
        // than the generic multilingual models. adb-pushed to the app's external-files dir.
        const val MODEL_FILE = "ggml-hindi-small-q5_1.bin"
    }

    private val modelFile: File
        get() = File(context.getExternalFilesDir(null), MODEL_FILE)

    private val mutex = Mutex()
    @Volatile private var whisper: WhisperContext? = null

    fun isModelPresent(): Boolean = modelFile.exists()

    val voiceEnabled: Boolean get() = BuildConfig.VOICE_ENABLED

    /** Loads the model once (off the main thread). Serialized so it can't double-load. */
    private suspend fun loadedContext(): WhisperContext = mutex.withLock {
        whisper ?: withContext(Dispatchers.Default) {
            WhisperContext.createFromFile(modelFile.absolutePath)
        }.also {
            whisper = it
            Timber.i("Whisper model loaded from %s", modelFile.absolutePath)
        }
    }

    /** Pre-load the model (call when the entry screen opens) so the first transcription is fast. */
    suspend fun warmUp() {
        if (isModelPresent()) runCatching { loadedContext() }
    }

    /** Transcribe normalized 16 kHz mono samples to text. Loads the model on first call. */
    suspend fun transcribe(audio: FloatArray, lang: String = "hi"): String {
        val preloaded = whisper != null
        val loadStart = System.currentTimeMillis()
        val ctx = loadedContext()
        val loadMs = System.currentTimeMillis() - loadStart
        val seconds = audio.size / 16000.0
        val t0 = System.currentTimeMillis()
        val text = ctx.transcribe(audio, lang)
        val transMs = System.currentTimeMillis() - t0
        Timber.i(
            "Whisper timing: audio=%.1fs, preloaded=%b, loadWait=%dms, transcribe=%dms",
            seconds, preloaded, loadMs, transMs,
        )
        return text
    }
}
