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

/** MP3 插件（JavaCV/FFmpeg，LOSSY）。 */
public final class Mp3Plugin implements FormatPlugin {

    @Override public String id() { return "mp3.codec"; }
    @Override public FormatFamily family() { return FormatFamily.AUDIO_STREAM; }
    @Override public Set<String> extensions() { return Set.of("mp3"); }
    @Override public String mimeType() { return "audio/mpeg"; }
    @Override public Capability capability() { return Capability.LOSSY; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 3) return false;
        // ID3 tag
        if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') return true;
        // MPEG 帧同步: 0xFF 0xEx
        if ((header[0] & 0xFF) == 0xFF && (header[1] & 0xE0) == 0xE0) return true;
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            return JavaCvMedia.decodeAudio(data, ".mp3", pm);
        } catch (IOException e) {
            throw new ConversionException("MP3 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return AudioStream.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof AudioStream audio)) {
            throw new ConversionException("MP3 编码需要 AudioStream，收到 " + model.getClass().getSimpleName());
        }
        try (OutputStream out = sink.openStream()) {
            JavaCvMedia.encodeAudio(audio, out, "mp3", ".mp3");
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("MP3 写入失败: " + e.getMessage(), e);
        }
    }
}