package com.artha.kirana.data.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Captures microphone audio as 16 kHz mono PCM and returns float samples normalized to
 * [-1, 1] — the format whisper.cpp expects. Recording runs until [isRecording] goes false,
 * the coroutine is cancelled, or [maxSeconds] is reached.
 */
class AudioRecorder @Inject constructor() {

    companion object {
        const val SAMPLE_RATE = 16_000

        // int16 RMS below this is effectively silence (speech is typically > 1000).
        private const val SILENCE_RMS = 200.0
    }

    /** Caller MUST hold RECORD_AUDIO before invoking. */
    @SuppressLint("MissingPermission")
    suspend fun record(maxSeconds: Int = 10, isRecording: () -> Boolean): FloatArray =
        withContext(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = maxOf(minBuf, SAMPLE_RATE) // ~0.5s of int16
            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            val maxSamples = SAMPLE_RATE * maxSeconds
            var pcm = ShortArray(maxSamples)
            var total = 0
            val chunk = ShortArray(bufferSize)
            try {
                recorder.startRecording()
                while (coroutineContext.isActive && isRecording() && total < maxSamples) {
                    val read = recorder.read(chunk, 0, chunk.size)
                    if (read <= 0) continue
                    val n = minOf(read, maxSamples - total)
                    System.arraycopy(chunk, 0, pcm, total, n)
                    total += n
                }
            } finally {
                runCatching { recorder.stop() }
                recorder.release()
            }
            // Reject near-silent captures — whisper hallucinates text on silence/noise.
            var sumSq = 0.0
            for (i in 0 until total) {
                val s = pcm[i].toDouble()
                sumSq += s * s
            }
            val rms = if (total > 0) kotlin.math.sqrt(sumSq / total) else 0.0
            if (total == 0 || rms < SILENCE_RMS) return@withContext FloatArray(0)

            FloatArray(total) { (pcm[it] / 32767.0f).coerceIn(-1f, 1f) }
        }
}
