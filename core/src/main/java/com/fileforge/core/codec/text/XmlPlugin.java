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
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** XML 插件（Jackson XML，LOSSLESS）。 */
public final class XmlPlugin implements FormatPlugin {

    @Override public String id() { return "xml.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("xml"); }
    @Override public String mimeType() { return "application/xml"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null) return false;
        String s = new String(header).trim();
        return s.startsWith("<?xml") || s.startsWith("<");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return TextCodecUtil.xmlToText(text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConversionException("XML 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return TextContent.class.isAssignableFrom(modelType) || com.fileforge.core.model.DataTable.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        try (OutputStream out = sink.openStream()) {
            String xml;
            if (model instanceof TextContent tc) {
                xml = TextCodecUtil.toXml(TextCodecUtil.parseXml(tc.text()));
            } else if (model instanceof com.fileforge.core.model.DataTable dt) {
                xml = TextCodecUtil.toXml(TextCodecUtil.tableToJsonText(dt));
            } else {
                throw new ConversionException("XML 编码不支持模型: " + model.getClass());
            }
            out.write(xml.getBytes(StandardCharsets.UTF_8));
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("XML 写入失败: " + e.getMessage(), e);
        }
    }
}