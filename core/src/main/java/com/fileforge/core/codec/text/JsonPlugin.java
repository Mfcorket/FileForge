package com.fileforge.core.codec.text;

import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** JSON 插件（Jackson，LOSSLESS）。 */
public final class JsonPlugin implements FormatPlugin {

    @Override public String id() { return "json.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("json"); }
    @Override public String mimeType() { return "application/json"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length == 0) return false;
        // 简单启发式：以 { 或 [ 开头（跳过 BOM/空白）
        for (byte b : header) {
            if (b == '{' || b == '[') return true;
            if (b != ' ' && b != '\t' && b != '\n' && b != '\r' && b != (byte)0xEF) return false;
        }
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            String text = new String(in.readAllBytes(), detectCharset(in));
            return TextCodecUtil.jsonToText(TextCodecUtil.parseJson(text), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConversionException("JSON 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return TextContent.class.isAssignableFrom(modelType) || com.fileforge.core.model.DataTable.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        try (OutputStream out = sink.openStream()) {
            String json;
            if (model instanceof TextContent tc) {
                json = TextCodecUtil.toJson(TextCodecUtil.textToJson(tc));
            } else if (model instanceof com.fileforge.core.model.DataTable dt) {
                json = TextCodecUtil.toJson(TextCodecUtil.tableToJsonText(dt));
            } else {
                throw new ConversionException("JSON 编码不支持模型: " + model.getClass());
            }
            out.write(json.getBytes(StandardCharsets.UTF_8));
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("JSON 写入失败: " + e.getMessage(), e);
        }
    }

    private Charset detectCharset(InputStream in) {
        // 简易：检测 UTF-8/16/32 BOM，否则 UTF-8
        return StandardCharsets.UTF_8;
    }
}