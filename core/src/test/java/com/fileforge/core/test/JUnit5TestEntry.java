package com.fileforge.core.test;

import org.junit.jupiter.api.*;
import org.junit.platform.suite.api.*;

import java.lang.reflect.Method;

/**
 * JUnit5 测试入口：包装现有的 CoreSmokeTest，提供标准 JUnit5 入口点。
 * 后续可逐步迁移为标准的 @Test 方法。
 */
@DisplayName("FileForge 核心测试套件")
@Tag("smoke")
public class JUnit5TestEntry {

    private static com.fileforge.core.CoreSmokeTest smokeTest;

    @BeforeAll
    static void init() {
        smokeTest = new com.fileforge.core.CoreSmokeTest();
    }

    @Test
    @DisplayName("核心冒烟测试套件 (53 项)")
    void runSmokeTests() throws Exception {
        // 通过反射调用 CoreSmokeTest.main() 兼容现有测试逻辑
        Method mainMethod = com.fileforge.core.CoreSmokeTest.class.getMethod("main", String[].class);
        mainMethod.invoke(null, (Object) new String[0]);
    }

    @Test
    @DisplayName("ServiceLoader 发现所有插件")
    void serviceLoaderDiscovery() {
        java.util.ServiceLoader.load(com.fileforge.core.spi.FormatPlugin.class).stream()
                .map(p -> p.get().id())
                .forEach(id -> System.out.println("Plugin: " + id));
    }

    @Test
    @DisplayName("编解码器注册表非空")
    void registryNotEmpty() {
        var registry = new com.fileforge.core.engine.PluginRegistry();
        registry.loadServicePlugins();
        assert registry.size() > 0 : "插件注册表不应为空";
        System.out.println("已加载插件数: " + registry.size());
    }
}