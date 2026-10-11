package com.fileforge.nativecode;

/**
 * Native 桥接（JNI）。
 * 加载 libfileforge_native.so/dll，提供音频/视频原生调用。
 */
public final class NativeBridge {
    static {
        // 从 core/libs/<os>/ 下加载
        String os = System.getProperty("os.name").toLowerCase();
        String libName = "fileforge_native";
        String libPath = System.getProperty("fileforge.native.lib.path",
                "core/libs/" + (os.contains("win") ? "win64" : os.contains("mac") ? "macos" : "linux") + "/" + System.mapLibraryName(libName));
        try {
            System.load(libPath);
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Native library not found at " + libPath + ": " + e.getMessage());
        }
    }

    private NativeBridge() {}

    // 版本
    public static native String nativeVersion();

    // 音频回调接口
    public interface AudioFrameCallback {
        void onAudioFrame(float[] samples, int channels);
    }

    // 设置音频回调（传入实现 AudioFrameCallback 的对象）
    public static native void nativeSetAudioCallback(AudioFrameCallback callback);

    // 解码：异步回调 AudioFrameCallback.onAudioFrame
    public static native int nativeAudioDecode(String inputPath, AudioFrameCallback callback);

    // 编码：同步
    public static native int nativeAudioEncode(String outputPath, int channels, int sampleRate, float[] samples);
}