#include "fileforge_native.h"
#include <jni.h>
#include <string>
#include <vector>
#include <cstring>

// 全局 JVM 指针（用于回调 Java）
static JavaVM* g_jvm = nullptr;
static jobject g_audio_callback_obj = nullptr;
static jmethodID g_audio_callback_mid = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_8;
}

JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    if (g_audio_callback_obj) {
        JNIEnv* env;
        vm->AttachCurrentThread((void**)&env, nullptr);
        env->DeleteGlobalRef(g_audio_callback_obj);
        g_audio_callback_obj = nullptr;
    }
}

// Java -> Native: 设置音频回调对象
extern "C" JNIEXPORT void JNICALL
Java_com_fileforge_native_NativeBridge_nativeSetAudioCallback(JNIEnv* env, jclass cls, jobject callback) {
    if (g_audio_callback_obj) env->DeleteGlobalRef(g_audio_callback_obj);
    g_audio_callback_obj = env->NewGlobalRef(callback);
    jclass cbCls = env->GetObjectClass(callback);
    g_audio_callback_mid = env->GetMethodID(cbCls, "onAudioFrame", "([FI)V"); // (float[], int)void
}

// Native 内部回调转发到 Java
static void native_audio_frame_cb(const float* samples, int num_samples, int channels, int sample_rate, void* user_data) {
    JNIEnv* env = nullptr;
    if (g_jvm->AttachCurrentThread((void**)&env, nullptr) != JNI_OK) return;

    jfloatArray jSamples = env->NewFloatArray(num_samples);
    env->SetFloatArrayRegion(jSamples, 0, num_samples, samples);

    if (g_audio_callback_obj && g_audio_callback_mid) {
        env->CallVoidMethod(g_audio_callback_obj, g_audio_callback_mid, jSamples, channels);
    }
    env->DeleteLocalRef(jSamples);
    g_jvm->DetachCurrentThread();
}

// Java -> Native: 解码音频文件
extern "C" JNIEXPORT jint JNICALL
Java_com_fileforge_native_NativeBridge_nativeAudioDecode(JNIEnv* env, jclass cls, jstring path) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int ret = ff_audio_decode(cpath, native_audio_frame_cb, nullptr);
    env->ReleaseStringUTFChars(path, cpath);
    return ret;
}

// Java -> Native: 编码音频
extern "C" JNIEXPORT jint JNICALL
Java_com_fileforge_native_NativeBridge_nativeAudioEncode(JNIEnv* env, jclass cls,
                                                         jstring path, jint channels, jint sampleRate,
                                                         jfloatArray samples) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    jfloat* arr = env->GetFloatArrayElements(samples, nullptr);
    int num_samples = env->GetArrayLength(samples);
    int ret = ff_audio_encode(cpath, channels, sampleRate, arr, num_samples);
    env->ReleaseFloatArrayElements(samples, arr, JNI_ABORT);
    env->ReleaseStringUTFChars(path, cpath);
    return ret;
}

// 版本
extern "C" JNIEXPORT jstring JNICALL
Java_com_fileforge_native_NativeBridge_nativeVersion(JNIEnv* env, jclass cls) {
    return env->NewStringUTF(ff_native_version());
}

// 纯 C 实现（无 FFmpeg 时返回错误，留待后续接入）
extern "C" const char* ff_native_version() {
    return "fileforge-native 0.1.0 (no-ffmpeg)";
}

extern "C" int ff_audio_decode(const char* input_path, ff_audio_frame_cb cb, void* user_data) {
    (void)input_path; (void)cb; (void)user_data;
    return -1; // 未实现，需 FFmpeg
}

extern "C" int ff_audio_encode(const char* output_path, int channels, int sample_rate,
                               const float* samples, int num_samples) {
    (void)output_path; (void)channels; (void)sample_rate; (void)samples; (void)num_samples;
    return -1;
}