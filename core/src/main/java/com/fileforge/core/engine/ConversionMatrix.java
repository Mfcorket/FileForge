package com.fileforge.core.engine;

import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.Model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 转换矩阵动态推导（规划文档 §4.4）：
 * A→B 可行 ⟺ 存在模型链 M0→M1→…→Mn，A.decode 产出 M0，B.encode 接受末尾模型类型；
 * 中间跳由 ModelAdapter 提供（多跳转换）。
 * <p>
 * 寻路按 <em>模型类型</em> 做 BFS：从 src 的 producedModel 出发，
 * 沿所有适配器的 to 类型扩展，直至找到 dst 的 supportsEncode 可接受的类型。
 */
public final class ConversionMatrix {

    private final ModelAdapters adapters;

    public ConversionMatrix(ModelAdapters adapters) {
        this.adapters = adapters;
    }

    /** 判断 src 能否转换为 dst（经 0..n 跳适配器）。 */
    public boolean canConvert(FormatPlugin src, FormatPlugin dst) {
        return findPath(src, dst) != null;
    }

    /** 计算 src→dst 的模型适配路径（null 表示不可达）。 */
    public List<ModelAdapter> findPath(FormatPlugin src, FormatPlugin dst) {
        return findPath(src.producedModel(), dst);
    }

    /** 从给定模型类型出发，计算到 dst 的适配路径。 */
    public List<ModelAdapter> findPath(Class<? extends Model> fromModel, FormatPlugin dst) {
        // 优先 0 跳（dst 直接接受）
        if (dst.supportsEncode(fromModel)) return List.of();

        // BFS：节点 = 模型类型；边 = 适配器
        Deque<Class<? extends Model>> queue = new ArrayDeque<>();
        Deque<List<ModelAdapter>> paths = new ArrayDeque<>();
        Set<Class<? extends Model>> visited = new LinkedHashSet<>();
        queue.add(fromModel);
        paths.add(List.of());
        visited.add(fromModel);

        while (!queue.isEmpty()) {
            Class<? extends Model> cur = queue.poll();
            List<ModelAdapter> path = paths.poll();
            for (ModelAdapter a : adapters.adapters()) {
                if (!a.canAdapt(cur, a.toType())) continue;
                Class<? extends Model> next = a.toType();
                if (visited.contains(next)) continue;
                List<ModelAdapter> newPath = new java.util.ArrayList<>(path);
                newPath.add(a);
                if (dst.supportsEncode(next)) {
                    return newPath;
                }
                visited.add(next);
                queue.add(next);
                paths.add(newPath);
            }
        }
        return null;
    }

    /**
     * 给定输入插件，计算注册表中所有可行的目标插件
     * （含经多跳适配可达者）。
     */
    public Set<FormatPlugin> targetsFor(FormatPlugin src, PluginRegistry registry) {
        Set<FormatPlugin> out = new LinkedHashSet<>();
        for (FormatPlugin dst : registry.plugins()) {
            if (dst != src && canConvert(src, dst)) {
                out.add(dst);
            }
        }
        return out;
    }
}