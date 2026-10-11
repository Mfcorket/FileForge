package com.fileforge.desktop;

import com.fileforge.core.engine.BatchConverter;
import com.fileforge.core.engine.ConversionEngine;
import com.fileforge.core.engine.PluginRegistry;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * FileForge 命令行入口（headless 批量转换）。
 *
 * 用法：
 *   java -cp <classpath> com.fileforge.desktop.CliMain --to png [--out DIR] file1 file2 ...
 *   java ... --list-formats
 *   java ... --formats <file>
 */
public final class CliMain {

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(2);
        }
        try {
            int code = run(args);
            System.exit(code);
        } catch (Throwable t) {
            System.err.println("错误: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        String targetExt = null;
        Path outDir = null;
        List<Path> inputs = new ArrayList<>();

        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--to", "-t" -> targetExt = args[++i];
                case "--out", "-o" -> outDir = Path.of(args[++i]);
                case "--list-formats" -> {
                    listFormats();
                    return 0;
                }
                case "--help", "-h" -> { printUsage(); return 0; }
                default -> inputs.add(Path.of(a));
            }
        }

        if (inputs.isEmpty()) {
            System.err.println("未指定输入文件");
            return 2;
        }
        if (targetExt == null) {
            System.err.println("未指定目标格式（--to）");
            return 2;
        }

        PluginRegistry registry = new PluginRegistry();
        registry.loadServicePlugins();
        ConversionEngine engine = new ConversionEngine(registry);

        if (outDir != null) Files.createDirectories(outDir);

        BatchConverter batch = new BatchConverter(engine);
        AtomicInteger ok = new AtomicInteger(), fail = new AtomicInteger();
        batch.onComplete(r -> {
            System.out.println((r.success() ? "[OK]   " : "[FAIL] ") + r.label()
                    + (r.error() == null ? "" : " — " + r.error()));
            if (r.success()) ok.incrementAndGet();
            else fail.incrementAndGet();
        });

        for (Path in : inputs) {
            String fileName = in.getFileName().toString();
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            Path dir = outDir != null ? outDir : (in.getParent() != null ? in.getParent() : Path.of("."));
            Path outPath = dir.resolve(baseName + "." + targetExt);
            batch.add(new BatchConverter.BatchItem(
                    fileName, InputSource.of(in), fileName, targetExt,
                    OutputSink.of(outPath), ProgressMonitor.NULL));
        }

        batch.start();
        // 等待完成（虚拟线程池任务结束）
        while (ok.get() + fail.get() < inputs.size()) {
            Thread.sleep(50);
        }
        System.out.println("完成: " + ok.get() + " 成功, " + fail.get() + " 失败");
        return fail.get() == 0 ? 0 : 1;
    }

    private static void listFormats() {
        PluginRegistry registry = new PluginRegistry();
        registry.loadServicePlugins();
        System.out.println("支持的格式（插件 ID → 扩展名）:");
        registry.plugins().stream().sorted(java.util.Comparator.comparing(p -> p.id())).forEach(p ->
                System.out.printf("  %-20s %s  (%s)%n", p.id(), p.extensions(), p.capability()));
    }

    private static void printUsage() {
        System.out.println("""
                FileForge 命令行批量转换工具
                用法:
                  fileforge --to <ext> [--out <dir>] <file>...
                    --to <ext>     目标格式扩展名 (png/bmp/jpeg/svg/pdf/csv/json/yaml/...)
                    --out <dir>    输出目录（默认同源目录）
                    --list-formats 列出所有支持的格式
                    --help         显示本帮助
                示例:
                  fileforge --to png a.pdf b.ai
                  fileforge --to csv --out ./out data.xlsx""");
    }
}