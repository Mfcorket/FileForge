package com.fileforge.core.codec.mmedia;

import com.fileforge.core.model.AudioStream;
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

/** FLAC 插件（JavaCV/FFmpeg，LOSSLESS，支持读+写）。 */
public final class FlacPlugin implements FormatPlugin {

    @Override public String id() { return "flac.codec"; }
    @Override public FormatFamily family() { return FormatFamily.AUDIO_STREAM; }
    @Override public Set<String> extensions() { return Set.of("flac"); }
    @Override public String mimeType() { return "audio/flac"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        return header[0] == 'f' && header[1] == 'L' && header[2] == 'a' && header[3] == 'C';
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            return JavaCvMedia.decodeAudio(data, ".flac", pm);
        } catch (IOException e) {
            throw new ConversionException("FLAC 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return AudioStream.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof AudioStream audio)) {
            throw new ConversionException("FLAC 编码需要 AudioStream");
        }
        try (OutputStream out = sink.openStream()) {
            JavaCvMedia.encodeAudio(audio, out, "flac", ".flac");
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("FLAC 写入失败: " + e.getMessage(), e);
        }
    }
}