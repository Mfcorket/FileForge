package com.fileforge.core.engine;

import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.util.Platform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 插件注册表。线程安全。
 * 默认通过 ServiceLoader 加载（META-INF/services/com.fileforge.core.spi.FormatPlugin），
 * 也支持显式 register（便于测试与动态扩展）。
 * <p>
 * Android 环境下自动过滤 requiresDesktop() 为 true 的插件。
 */
public final class PluginRegistry {
    private final ConcurrentMap<String, FormatPlugin> byId = new ConcurrentHashMap<>();

    public void register(FormatPlugin plugin) {
        FormatPlugin prev = byId.putIfAbsent(plugin.id(), plugin);
        if (prev != null) {
            throw new IllegalStateException("duplicate plugin id: " + plugin.id());
        }
    }

    /** 加载 classpath 上所有 SPI 插件（Android 下自动过滤 requiresDesktop 的插件）。 */
    public void loadServicePlugins() {
        for (FormatPlugin p : ServiceLoader.load(FormatPlugin.class)) {
            if (Platform.isAndroid() && p.requiresDesktop()) {
                continue; // Android 上跳过需 java.desktop 的插件
            }
            register(p);
        }
    }

    public List<FormatPlugin> plugins() {
        return Collections.unmodifiableList(new ArrayList<>(byId.values()));
    }

    public Optional<FormatPlugin> byExtension(String ext) {
        String e = ext.toLowerCase(java.util.Locale.ROOT);
        return byId.values().stream().filter(p -> p.extensions().contains(e)).findFirst();
    }

    /**
     * 获取指定族的所有插件（已按平台过滤）。
     */
    public List<FormatPlugin> pluginsByFamily(com.fileforge.core.spi.FormatFamily family) {
        return byId.values().stream()
                .filter(p -> p.family() == family)
                .collect(Collectors.toList());
    }

    public int size() {
        return byId.size();
    }
}
