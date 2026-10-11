package com.fileforge.core.codec.office;

import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PowerPoint (.pptx) 插件（Apache POI，解码为 TextContent；提取幻灯片文本）。
 * 编码暂不支持（仅解码导出）。
 */
public final class PptxPlugin implements FormatPlugin {

    @Override public String id() { return "pptx.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("pptx"); }
    @Override public String mimeType() { return "application/vnd.openxmlformats-officedocument.presentationml.presentation"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        return (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            XMLSlideShow ppt = new XMLSlideShow(in);
            StringBuilder sb = new StringBuilder();
            List<XSLFSlide> slides = ppt.getSlides();
            for (int i = 0; i < slides.size(); i++) {
                XSLFSlide slide = slides.get(i);
                sb.append("=== Slide ").append(i + 1).append(" ===\n");
                for (var shape : slide.getPlaceholders()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    }
                }
                // 也遍历所有形状
                for (var shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape ts) {
                        String t = ts.getText();
                        if (t != null && !t.isBlank()) sb.append(t).append('\n');
                    }
                }
                if (i % 5 == 0) pm.progress((int) ((long) i * 80 / slides.size()));
            }
            return new TextContent(sb.toString(), StandardCharsets.UTF_8, Map.of("slides", String.valueOf(slides.size())));
        } catch (IOException e) {
            throw new ConversionException("PPTX 解析失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConversionException("PPTX 解析异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return false;
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("PPTX 写入暂不支持（仅解码导出）");
    }
}