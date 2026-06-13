// JNI bridge for whisper.cpp on Android. Adapted from whisper.cpp/examples/whisper.android.
// Kotlin counterpart: com.whispercpp.whisper.WhisperLib (companion object methods).
// fullTranscribe takes a language code (e.g. "hi" for Hindi) — the upstream sample hardcodes "en".
#include <jni.h>
#include <android/log.h>
#include <string.h>
#include "whisper.h"
#include "ggml.h"

#define UNUSED(x) (void)(x)
#define TAG "WhisperJNI"
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

JNIEXPORT jlong JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    UNUSED(thiz);
    const char *model_path = (*env)->GetStringUTFChars(env, model_path_str, NULL);
    struct whisper_context_params cparams = whisper_context_default_params();
    struct whisper_context *context = whisper_init_from_file_with_params(model_path, cparams);
    (*env)->ReleaseStringUTFChars(env, model_path_str, model_path);
    return (jlong) context;
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    whisper_free((struct whisper_context *) context_ptr);
}

JNIEXPORT void JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint num_threads, jstring lang_str,
        jfloatArray audio_data) {
    UNUSED(thiz);
    struct whisper_context *context = (struct whisper_context *) context_ptr;
    jfloat *audio = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize n = (*env)->GetArrayLength(env, audio_data);
    const char *lang = (*env)->GetStringUTFChars(env, lang_str, NULL);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = lang;          // "hi" for Hindi; valid for the duration of whisper_full
    params.n_threads = num_threads;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = true;    // short sale utterances → one segment, faster

    whisper_reset_timings(context);
    if (whisper_full(context, params, audio, n) != 0) {
        LOGW("whisper_full failed");
    } else {
        whisper_print_timings(context); // encode/decode breakdown → logcat (tag "whisper")
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio, JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env, lang_str, lang);
}

JNIEXPORT jint JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    UNUSED(env);
    UNUSED(thiz);
    return whisper_full_n_segments((struct whisper_context *) context_ptr);
}

// Returns the raw UTF-8 bytes (NOT NewStringUTF, which requires Modified UTF-8 and ABORTS the
// process on whisper's standard UTF-8 / stray token-boundary bytes — e.g. Devanagari). Kotlin
// decodes these as UTF-8, which replaces invalid bytes instead of crashing.
JNIEXPORT jbyteArray JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    UNUSED(thiz);
    const char *text = whisper_full_get_segment_text((struct whisper_context *) context_ptr, index);
    const jsize len = (jsize) strlen(text);
    jbyteArray arr = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, arr, 0, len, (const jbyte *) text);
    return arr;
}

JNIEXPORT jstring JNICALL
Java_com_whispercpp_whisper_WhisperLib_00024Companion_getSystemInfo(
        JNIEnv *env, jobject thiz) {
    UNUSED(thiz);
    return (*env)->NewStringUTF(env, whisper_print_system_info());
}
