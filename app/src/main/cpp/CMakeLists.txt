cmake_minimum_required(VERSION 3.10)
project(whisper_android)

set(CMAKE_CXX_STANDARD 17)

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
