package com.fileforge.core.codec.mmedia;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.model.VideoStream;
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
 * 视频插件（JavaCV/FFmpeg，mp4/mkv/webm/mov/avi）。
 * 解码为 VideoStream（RGBA 帧列表，最多 MAX_FRAMES 抽样帧）；编码暂不支持。
 */
public final class VideoPlugin implements FormatPlugin {

    /** 内存保护：单次解码最大缓存的帧数（RGBA 大图很占内存）。 */
    private static final int MAX_FRAMES = 120;

    @Override public String id() { return "video.codec"; }
    @Override public FormatFamily family() { return FormatFamily.VIDEO_STREAM; }
    @Override public Set<String> extensions() { return Set.of("mp4", "mkv", "webm", "mov", "avi", "m4v"); }
    @Override public String mimeType() { return "video/mp4"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // MKV/WebM (EBML): 1A 45 DF A3
        if ((header[0] & 0xFF) == 0x1A && (header[1] & 0xFF) == 0x45
                && (header[2] & 0xFF) == 0xDF && (header[3] & 0xFF) == 0xA3) return true;
        if (header.length < 8) return false;
        // MP4/MOV: "....ftyp"
        if (header[4] == 'f' && header[5] == 't' && header[6] == 'y' && header[7] == 'p') return true;
        if (header.length < 12) return false;
        // AVI: "RIFF" ... "AVI "
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                && header[8] == 'A' && header[9] == 'V' && header[10] == 'I') return true;
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            return JavaCvMedia.decodeVideo(data, ".mp4", MAX_FRAMES, pm);
        } catch (IOException e) {
            throw new ConversionException("视频读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return VideoStream.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof VideoStream video)) {
            throw new ConversionException("视频编码需要 VideoStream，收到 " + model.getClass().getSimpleName());
        }
        try (OutputStream out = sink.openStream()) {
            // 目标扩展名决定容器格式（mp4/mkv/webm/mov）
            String ext = video.metadata().getOrDefault("targetExt", "mp4");
            JavaCvMedia.encodeVideo(video, out, ext, "." + ext);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("视频写入失败: " + e.getMessage(), e);
        }
    }
}