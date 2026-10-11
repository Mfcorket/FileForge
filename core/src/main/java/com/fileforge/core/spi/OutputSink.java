package com.fileforge.core.spi;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** 输出汇：编码结果的写入目标。 */
public interface OutputSink {
    OutputStream openStream() throws IOException;

    static OutputSink of(Path path) {
        return () -> Files.newOutputStream(path);
    }
}
