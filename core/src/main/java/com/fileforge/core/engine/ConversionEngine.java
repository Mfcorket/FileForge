package com.fileforge.core.engine;

import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * 转换引擎：输入文件 → Detector → decode → 中间模型(可多跳适配) → encode → 输出文件（§3.3/§4.4）。
 * <p>
 * 多跳：src 解码出的模型类型若不被 dst 直接接受，按转换矩阵 BFS 得到的适配器链逐跳转换。
 * 警告：解码/适配过程产生的警告经 {@link ProgressMonitor#warning(String)} 上报。
 */
public final class ConversionEngine {
    private final PluginRegistry registry;
    private final FormatDetector detector;
    private final ModelAdapters adapters = new ModelAdapters();
    private final ConversionMatrix matrix = new ConversionMatrix(adapters);

    public ConversionEngine(PluginRegistry registry) {
        this.registry = registry;
        this.detector = new FormatDetector(registry);
    }

    public PluginRegistry registry() { return registry; }

    public FormatDetector detector() { return detector; }

    public ModelAdapters adapters() { return adapters; }

    public ConversionMatrix matrix() { return matrix; }

    /**
     * 执行一次转换（0..多跳自动适配）。
     *
     * @param source      输入源
     * @param fileName    原始文件名（用于扩展名回退检测）
     * @param targetExt   目标格式扩展名（小写、不含点）
     * @param sink        输出汇
     * @param pm          进度监视器
     */
    public void convert(InputSource source, String fileName, String targetExt,
                        OutputSink sink, ProgressMonitor pm) throws ConversionException {
        Optional<FormatPlugin> srcOpt;
        try {
            srcOpt = detector.detect(source, fileName);
        } catch (IOException e) {
            throw new ConversionException("读取输入失败: " + fileName, e);
        }
        FormatPlugin src = srcOpt.orElseThrow(
                () -> new ConversionException("无法识别输入格式: " + fileName));

        String ext = targetExt.toLowerCase(java.util.Locale.ROOT);
        FormatPlugin dst = registry.byExtension(ext).orElseThrow(
                () -> new ConversionException("不支持的目标格式: " + targetExt));

        Model model = src.decode(source, pm);

        // 解析真实解码类型，避免与静态 producedModel 不一致
        Class<? extends Model> actualType = model.getClass();

        List<ModelAdapter> path = matrix.findPath(actualType, dst);
        if (path == null) {
            throw new ConversionException(
                    "转换路径不存在: " + src.id() + " → " + dst.id() + "（无模型适配链）");
        }

        for (ModelAdapter a : path) {
            if (pm.isCancelled()) throw new ConversionException("已取消");
            model = a.convert(model);
        }
        if (!dst.supportsEncode(model.getClass())) {
            throw new ConversionException(
                    "目标插件不接受模型 " + model.getClass().getSimpleName() + ": " + dst.id());
        }
        dst.encode(model, sink, pm);
        pm.progress(100);
    }
}