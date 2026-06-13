# Native ASR — whisper.cpp (Phase 4 voice)

- `whisper/` — vendored **whisper.cpp v1.7.4** (`https://github.com/ggml-org/whisper.cpp`), libs only:
  `src/ ggml/ include/ cmake/ CMakeLists.txt` (coreml/openvino backends + examples/tests/bindings removed).
- `jni.c` — JNI bridge (adapted from `examples/whisper.android`). Adds a `lang` param to
  `fullTranscribe` so we can pass `"hi"` (upstream hardcodes `"en"`). Native lib = `libwhisper_android.so`.
- `CMakeLists.txt` — builds ggml + whisper (CPU only, `GGML_NATIVE/OPENMP/LLAMAFILE OFF`) via
  `add_subdirectory(whisper)`, then the JNI lib. Wired through `app/build.gradle.kts`
  `externalNativeBuild`, `arm64-v8a` only (the iQOO).

Kotlin side: `com.whispercpp.whisper.WhisperLib` (JNI decls — package/name must match the mangled
symbols) and `WhisperContext` (one native context, single dedicated thread). App-facing wrapper:
`com.artha.kirana.data.voice.WhisperEngine` (lazy singleton, loads the ggml model from
`/sdcard/Download/ggml-tiny.bin`, adb-pushed).

To bump whisper.cpp: re-copy those subdirs from a clean clone at the new tag and update this note.
