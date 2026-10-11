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
 * GIF 插件（JDK ImageIO 实现，JAVALIB 来源，有损：调色板量化 + 动画仅取首帧）。
 */
public final class GifPlugin implements FormatPlugin {

    @Override public String id() { return "gif.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("gif"); }
    @Override public String mimeType() { return "image/gif"; }
    @Override public Capability capability() { return Capability.LOSSY; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 6) return false;
        return header[0] == 'G' && header[1] == 'I' && header[2] == 'F' && header[3] == '8'
                && (header[4] == '7' || header[4] == '9') && header[5] == 'a';
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (var in = source.openStream()) {
            RasterImage img = ImageIoBridge.decode(in, "GIF");
            pm.progress(90);
            return img;
        } catch (IOException e) {
            throw new ConversionException("GIF 读取失败", e);
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
            throw new ConversionException("GIF 编码需要 RasterImage 模型，收到: " + model.getClass());
        }
        try (var out = sink.openStream()) {
            ImageIoBridge.encode(img, out, "gif", false);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("GIF 写入失败", e);
        }
    }
}
