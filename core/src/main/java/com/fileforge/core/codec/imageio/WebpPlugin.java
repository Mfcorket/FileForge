package com.fileforge.core.codec.imageio;

import com.fileforge.core.model.RasterImage;
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
import java.util.Set;

/**
 * WebP 插件（ImageIO，若环境支持则编解码；不支持时报错）。
 * WebP 有损/无损均支持，ImageIO 需 SPI 插件（如 twelvemonkeys）。
 */
public final class WebpPlugin implements FormatPlugin {

    @Override public String id() { return "webp.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("webp"); }
    @Override public String mimeType() { return "image/webp"; }
    @Override public Capability capability() { return Capability.LOSSY; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 12) return false;
        // RIFF....WEBP
        return header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            RasterImage img = ImageIoBridge.decode(in, "webp");
            pm.progress(90);
            return img;
        } catch (IOException e) {
            throw new ConversionException("WebP 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return RasterImage.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof RasterImage img)) {
            throw new ConversionException("WebP 编码需要 RasterImage，收到 " + model.getClass().getSimpleName());
        }
        try (OutputStream out = sink.openStream()) {
            ImageIoBridge.encode(img, out, "webp", false);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("WebP 写入失败: " + e.getMessage(), e);
        }
    }
}