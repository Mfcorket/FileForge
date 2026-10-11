package com.fileforge.core.codec.bmp;

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

/** BMP 格式插件（自研实现，无损）。 */
public final class BmpPlugin implements FormatPlugin {

    @Override public String id() { return "bmp.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("bmp"); }
    @Override public String mimeType() { return "image/bmp"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        return BmpCodec.matches(header);
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (var in = source.openStream()) {
            return BmpCodec.decode(in, pm);
        } catch (IOException e) {
            throw new ConversionException("BMP 读取失败", e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return RasterImage.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof RasterImage img)) {
            throw new ConversionException("BMP 编码需要 RasterImage 模型，收到: " + model.getClass());
        }
        try (var out = sink.openStream()) {
            BmpCodec.encode(img, out, pm);
        } catch (IOException e) {
            throw new ConversionException("BMP 写入失败", e);
        }
    }
}
