package com.fileforge.core.codec.font;

import com.fileforge.core.model.FontInfo;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 字体插件（TTF/OTF）：基于 java.awt.Font 读取基础元数据。
 * 仅解码为 FontInfo（编码暂不支持）。
 */
public final class FontPlugin implements FormatPlugin {

    @Override public String id() { return "font.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("ttf", "otf"); }
    @Override public String mimeType() { return "font/ttf"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // TTF/OTF: "OTTO" (0x4F54544F) or "\0\0\1\0" (0x00010000)
        return (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O')
                || (header[0] == 0 && header[1] == 0 && header[2] == 1 && header[3] == 0);
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try {
            Path tmp = Files.createTempFile("font-", ".ttf");
            try (InputStream in = source.openStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, Files.newInputStream(tmp));
            font = font.deriveFont(12f);
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            ge.registerFont(font);

            String family = font.getFamily();
            String fullName = family; // Font API 不直接暴露 fullName/postScriptName/version
            String psName = family;
            String version = "1.0";
            int unitsPerEm = 1000;
            int weightClass = mapWeight(font.getStyle());
            boolean fixedPitch = isFixedPitch(font);

            List<Integer> ranges = new ArrayList<>();
            if (font.canDisplayUpTo("ABC") == -1) ranges.add(0x0000);
            if (font.canDisplayUpTo("ÀÁÂ") == -1) ranges.add(0x0080);

            FontInfo info = new FontInfo(family, fullName, psName, version,
                    unitsPerEm, weightClass, fixedPitch, ranges, Map.of("file", tmp.getFileName().toString()));
            pm.progress(90);
            return info;
        } catch (IOException | FontFormatException e) {
            throw new ConversionException("字体解析失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConversionException("字体解析异常: " + e.getMessage(), e);
        }
    }

    private int mapWeight(int style) {
        if ((style & Font.BOLD) != 0) return 700;
        return 400;
    }

    private boolean isFixedPitch(Font font) {
        try {
            return (boolean) Font.class.getMethod("isFixedPitch").invoke(font);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) { return false; }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("字体写入暂不支持");
    }
}