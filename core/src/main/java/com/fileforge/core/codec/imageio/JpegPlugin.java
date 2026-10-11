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
import java.util.Set;

/**
 * JPEG 插件（JDK ImageIO 实现，JAVALIB 来源，有损）。
 * 编码时丢弃 Alpha（JPEG 不支持透明）。
 */
public final class JpegPlugin implements FormatPlugin {

    @Override public String id() { return "jpeg.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("jpg", "jpeg", "jpe"); }
    @Override public String mimeType() { return "image/jpeg"; }
    @Override public Capability capability() { return Capability.LOSSY; }

    @Override
    public boolean canDecode(byte[] header) {
        // JPEG SOI: FF D8 FF
        return header != null && header.length >= 3
                && (header[0] & 0xFF) == 0xFF && (header[1] & 0xFF) == 0xD8 && (header[2] & 0xFF) == 0xFF;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (var in = source.openStream()) {
            RasterImage img = ImageIoBridge.decode(in, "JPEG");
            pm.progress(90);
            return img;
        } catch (IOException e) {
            throw new ConversionException("JPEG 读取失败", e);
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
            throw new ConversionException("JPEG 编码需要 RasterImage 模型，收到: " + model.getClass());
        }
        try (var out = sink.openStream()) {
            ImageIoBridge.encode(img, out, "jpeg", true);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("JPEG 写入失败", e);
        }
    }
}
