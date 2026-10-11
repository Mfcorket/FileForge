package com.fileforge.core.engine;

import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;

import java.io.IOException;
import java.util.Optional;

/**
 * 格式检测器：优先魔数嗅探，回退扩展名匹配。
 */
public final class FormatDetector {
    private static final int HEADER_BYTES = 512;

    private final PluginRegistry registry;

    public FormatDetector(PluginRegistry registry) {
        this.registry = registry;
    }

    /** 依据文件头魔数检测可解码插件。 */
    public Optional<FormatPlugin> detect(InputSource source) throws IOException {
        byte[] header = source.readHeader(HEADER_BYTES);
        return registry.plugins().stream().filter(p -> p.canDecode(header)).findFirst();
    }

    /** 魔数失败时按扩展名回退。 */
    public Optional<FormatPlugin> detect(InputSource source, String fileName) throws IOException {
        Optional<FormatPlugin> byMagic = detect(source);
        if (byMagic.isPresent()) {
            return byMagic;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Optional.empty();
        }
        return registry.byExtension(fileName.substring(dot + 1));
    }
}
