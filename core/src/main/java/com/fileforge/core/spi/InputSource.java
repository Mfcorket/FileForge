package com.fileforge.core.spi;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 输入源：一次转换生命周期内可读取的输入。 */
public interface InputSource {
    /** 打开新的输入流（调用方负责关闭）。 */
    InputStream openStream() throws IOException;

    /** 读取文件头若干字节用于魔数嗅探。 */
    default byte[] readHeader(int maxBytes) throws IOException {
        try (InputStream in = openStream()) {
            return in.readNBytes(maxBytes);
        }
    }

    static InputSource of(Path path) {
        return () -> Files.newInputStream(path);
    }

    static InputSource of(byte[] data) {
        return () -> new java.io.ByteArrayInputStream(data);
    }
}
