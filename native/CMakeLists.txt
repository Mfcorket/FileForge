cmake_minimum_required(VERSION 3.20)
project(fileforge-native LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# FFmpeg 查找（可选，找到则启用完整音视频）
find_package(PkgConfig QUIET)
pkg_check_modules(FFMPEG QUIET libavcodec libavformat libavutil libswresample libswscale)

if (FFMPEG_FOUND)
    message(STATUS "FFmpeg found: ${FFMPEG_LIBRARIES}")
    add_definitions(-DHAVE_FFMPEG=1)
else()
    message(WARNING "FFmpeg not found, native audio/video will be disabled (pure Java fallback)")
    add_definitions(-DHAVE_FFMPEG=0)
endif()

# JNI 头文件
find_package(JNI REQUIRED)
include_directories(${JNI_INCLUDE_DIRS})

# 共享库
add_library(fileforge_native SHARED
    src/main/cpp/jni/native_bridge.cpp
    src/main/cpp/audio/audio_codec.cpp
)

target_link_libraries(fileforge_native ${JNI_LIBRARIES})
if (FFMPEG_FOUND)
    target_link_libraries(fileforge_native ${FFMPEG_LIBRARIES})
endif()

# 安装到 native/libs/${CMAKE_SYSTEM_NAME}
set(INSTALL_DIR ${CMAKE_BINARY_DIR}/../../core/libs/${CMAKE_SYSTEM_NAME})
install(TARGETS fileforge_native DESTINATION ${INSTALL_DIR})