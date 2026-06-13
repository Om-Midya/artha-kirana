package com.whispercpp.whisper

// JNI declarations for libwhisper_android.so (see app/src/main/cpp/jni.c).
// The package + class + companion names must stay in sync with the native symbol mangling
// (Java_com_whispercpp_whisper_WhisperLib_00024Companion_*).
internal class WhisperLib {
    companion object {
        init {
            System.loadLibrary("whisper_android")
        }

        external fun initContext(modelPath: String): Long
        external fun freeContext(contextPtr: Long)
        external fun fullTranscribe(contextPtr: Long, numThreads: Int, lang: String, audioData: FloatArray)
        external fun getTextSegmentCount(contextPtr: Long): Int

        /** Raw UTF-8 bytes of a segment — decode in Kotlin (see [WhisperContext]). */
        external fun getTextSegment(contextPtr: Long, index: Int): ByteArray
        external fun getSystemInfo(): String
    }
}
