#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#if defined(_WIN32)
  #define FF_NATIVE_EXPORT __declspec(dllexport)
#else
  #define FF_NATIVE_EXPORT __attribute__((visibility("default")))
#endif

// 版本
FF_NATIVE_EXPORT const char* ff_native_version();

// 音频解码：输入文件路径，输出 PCM 到回调
// 返回 0 成功，负值错误码
typedef void (*ff_audio_frame_cb)(const float* samples, int num_samples, int channels, int sample_rate, void* user_data);

FF_NATIVE_EXPORT int ff_audio_decode(const char* input_path, ff_audio_frame_cb cb, void* user_data);

// 音频编码：输入 PCM 浮点流，写入文件
FF_NATIVE_EXPORT int ff_audio_encode(const char* output_path, int channels, int sample_rate,
                                     const float* samples, int num_samples);

#ifdef __cplusplus
}
#endif