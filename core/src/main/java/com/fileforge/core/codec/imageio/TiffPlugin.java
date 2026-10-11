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
 * TIFF 插件（ImageIO，支持多页读取首页，LOSSLESS）。
 * 支持 LZW/PackBits/无压缩等 TIFF 变体（ImageIO 内置）。
 */
public final class TiffPlugin implements FormatPlugin {

    @Override public String id() { return "tiff.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("tif", "tiff"); }
    @Override public String mimeType() { return "image/tiff"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // TIFF: II*\0 或 MM\0*
        if (header[0] == 'I' && header[1] == 'I' && header[2] == 42 && header[3] == 0) return true;
        if (header[0] == 'M' && header[1] == 'M' && header[2] == 0 && header[3] == 42) return true;
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            RasterImage img = ImageIoBridge.decode(in, "tiff");
            pm.progress(90);
            return img;
        } catch (IOException e) {
            throw new ConversionException("TIFF 读取失败: " + e.getMessage(), e);
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
            throw new ConversionException("TIFF 编码需要 RasterImage，收到 " + model.getClass().getSimpleName());
        }
        try (OutputStream out = sink.openStream()) {
            ImageIoBridge.encode(img, out, "tiff", true);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("TIFF 写入失败: " + e.getMessage(), e);
        }
    }
}