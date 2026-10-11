package com.fileforge.core.test;

import com.fileforge.core.engine.ConversionEngine;
import com.fileforge.core.engine.PluginRegistry;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.zip.Adler32;

import org.junit.jupiter.api.io.TempDir;

/**
 * 测试基类：提供通用的测试工具和公共 fixture。
 */
public abstract class TestBase {

    protected ConversionEngine engine;
    protected TestResources resources;

    @BeforeEach
    void setUp() {
        var registry = new com.fileforge.core.engine.PluginRegistry();
        registry.loadServicePlugins();
        this.engine = new com.fileforge.core.engine.ConversionEngine(registry);
        this.resources = new TestResources();
    }

    @AfterEach
    void tearDown() {
        if (resources != null) {
            resources.cleanup();
        }
    }

    /**
     * 计算字节数组的 Adler32 校验和，用于快速比对二进制内容。
     */
    protected long checksum(byte[] data) {
        Adler32 adler = new Adler32();
        adler.update(data);
        return adler.getValue();
    }

    /**
     * 断言两个字节数组内容相同（通过校验和比对）。
     */
    protected void assertBytesEqual(byte[] expected, byte[] actual, String message) {
        org.junit.jupiter.api.Assertions.assertEquals(
                checksum(expected), checksum(actual),
                message + " (checksum mismatch)"
        );
    }

    /**
     * 执行完整转换流程：输入文件 -> 引擎 -> 输出字节数组。
     */
    protected byte[] convert(byte[] input, String sourceFileName, String targetExt) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.fileforge.core.engine.ConversionEngine engine = this.engine;
        engine.convert(
                new com.fileforge.core.spi.InputSource.ByteArraySource(input),
                "test." + inferExt(input),
                targetExt,
                () -> new java.io.ByteArrayOutputStream(),
                com.fileforge.core.spi.ProgressMonitor.NULL
        );
        // 注意：实际转换需要 engine.convert 的完整签名，这里简化
        throw new UnsupportedOperationException("Use convert() with OutputSink");
    }

    private String inferExt(byte[] data) {
        if (data.length >= 4) {
            if (data[0] == 'P' && data[1] == 'N' && data[2] == 'G') return "png";
            if (data[0] == 'B' && data[1] == 'M') return "bmp";
            if (data[0] == (byte)0xFF && data[1] == (byte)0xD8) return "jpg";
            if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F') return "gif";
            if (data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F') return "pdf";
        }
        return "bin";
    }

    /**
     * 测试资源管理器：自动清理临时文件。
     */
    public static class TestResources implements AutoCloseable {
        private final java.util.List<java.nio.file.Path> tempFiles = new java.util.ArrayList<>();

        public Path createTempFile(String prefix, String suffix) throws IOException {
            java.nio.file.Path p = java.nio.file.Files.createTempFile("fileforge-test-", suffix);
            tempFiles.add(p);
            return p;
        }

        public void writeBytes(Path path, byte[] data) throws IOException {
            java.nio.file.Files.write(path, data);
        }

        public byte[] readBytes(Path path) throws IOException {
            return java.nio.file.Files.readAllBytes(path);
        }

        @Override
        public void close() {
            for (Path p : tempFiles) {
                try { java.nio.file.Files.deleteIfExists(p); } catch (IOException ignored) {}
            }
            tempFiles.clear();
        }
    }
}