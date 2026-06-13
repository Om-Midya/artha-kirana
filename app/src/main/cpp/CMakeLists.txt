cmake_minimum_required(VERSION 3.10)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

# CRITICAL: `installDebug` compiles native code at -O0 by default, which makes ggml ~30x slower
# (whisper base went 54s vs the ~1-2s it should take). Force -O3 + NDEBUG for ALL build types,
# and enable the 8 Elite's armv8.2 SIMD (fp16 + dotprod) so ggml uses its fast arm64 kernels.
set(CMAKE_C_FLAGS_DEBUG     "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_DEBUG   "-O3 -DNDEBUG")
set(CMAKE_C_FLAGS_RELEASE   "-O3 -DNDEBUG")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")
if(ANDROID_ABI STREQUAL "arm64-v8a")
    add_compile_options(-O3 -march=armv8.2-a+fp16+dotprod)
endif()

# Build only the ggml + whisper libraries from the vendored tree (no examples/tests/server).
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
# Cross-compiling for Android: no host-native arch flags, no OpenMP runtime dependency.
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)

add_subdirectory(whisper ${CMAKE_BINARY_DIR}/whisper)

add_library(whisper_android SHARED jni.c)

target_include_directories(whisper_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper/include
    ${CMAKE_CURRENT_SOURCE_DIR}/whisper/ggml/include)

find_library(LOG_LIB log)
target_link_libraries(whisper_android whisper ${LOG_LIB} android)
