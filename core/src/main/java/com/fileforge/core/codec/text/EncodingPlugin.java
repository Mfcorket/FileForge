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

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 编码转换插件：输入任意编码文本 -> TextContent(UTF-8) -> 输出指定编码。
 * 仅作为工具插件，不注册为标准格式（extensions 为空），由 UI 显式调用。
 */
public final class EncodingPlugin implements FormatPlugin {

    @Override public String id() { return "encoding.convert"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of(); }
    @Override public String mimeType() { return "text/plain"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) { return false; }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            Charset cs = detectCharset(data);
            String text = new String(data, cs);
            return new TextContent(text, StandardCharsets.UTF_8, Map.of("sourceCharset", cs.name()));
        } catch (IOException e) {
            throw new ConversionException("编码检测/读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return TextContent.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof TextContent tc)) {
            throw new ConversionException("EncodingPlugin 仅支持 TextContent");
        }
        Charset target = Charset.forName(tc.metadata().getOrDefault("targetCharset", "UTF-8"));
        try (OutputStream out = sink.openStream()) {
            out.write(tc.text().getBytes(target));
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("编码转换写入失败: " + e.getMessage(), e);
        }
    }

    private Charset detectCharset(byte[] data) {
        try {
            CharsetDetector detector = new CharsetDetector();
            detector.setText(data);
            CharsetMatch match = detector.detect();
            if (match != null && match.getConfidence() > 50) {
                return Charset.forName(match.getName());
            }
        } catch (Exception ignored) {}
        return StandardCharsets.UTF_8;
    }
}