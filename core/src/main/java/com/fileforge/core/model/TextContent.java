package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.nio.charset.Charset;
import java.util.Map;

/**
 * 纯文本中间模型：文本内容 + 字符集 + 元数据。
 */
public final class TextContent implements Model {
    private final String text;
    private final Charset charset;
    private final Map<String, String> metadata;

    public TextContent(String text, Charset charset, Map<String, String> metadata) {
        this.text = text == null ? "" : text;
        this.charset = charset == null ? Charset.forName("UTF-8") : charset;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }

    public String text() { return text; }
    public Charset charset() { return charset; }
    public Map<String, String> metadata() { return metadata; }
}