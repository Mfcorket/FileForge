package com.fileforge.core.util;

/**
 * 平台检测工具。
 * <p>
 * 运行时识别 Android 环境，供 SPI 过滤插件使用。
 * 判断依据：系统属性 "java.vendor"、"os.name"、类 "android.os.Build" 是否存在。
 */
public final class Platform {

    private Platform() {}

    private static final Boolean IS_ANDROID = detectAndroid();

    private static boolean detectAndroid() {
        // 1. 标准 Android 系统属性
        String osName = System.getProperty("os.name", "").toLowerCase();
        if (osName.contains("android")) return true;

        // 2. Android 特有系统属性
        String javaVendor = System.getProperty("java.vendor", "").toLowerCase();
        if (javaVendor.contains("android")) return true;

        // 3. Android 运行时特有类
        try {
            Class.forName("android.os.Build");
            return true;
        } catch (ClassNotFoundException ignored) {}

        // 4. Android 运行时特有系统属性
        String runtime = System.getProperty("java.runtime.name", "").toLowerCase();
        if (runtime.contains("android")) return true;

        return false;
    }

    /** 当前是否在 Android 运行环境。 */
    public static boolean isAndroid() {
        return IS_ANDROID;
    }

    /** 当前是否为桌面/服务器环境。 */
    public static boolean isDesktop() {
        return !IS_ANDROID;
    }
}