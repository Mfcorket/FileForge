package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 视频流中间模型：帧序列 + 元数据。
 * 帧为 RGBA8888 RasterImage（JavaCV/FFmpeg 解码后转换）。
 * 注意：完整解码加载所有帧到内存，大视频需分帧式处理（后续优化为流式）。
 */
public final class VideoStream implements Model {
    private final int frameWidth;
    private final int frameHeight;
    private final double fps;
    private final long frameCount;
    private final List<RasterImage> frames;
    private final Map<String, String> metadata;

    public VideoStream(int frameWidth, int frameHeight, double fps, long frameCount,
                       List<RasterImage> frames, Map<String, String> metadata) {
        this.frameWidth = frameWidth;
        this.frameHeight = frameHeight;
        this.fps = fps;
        this.frameCount = frameCount;
        this.frames = frames == null ? List.of() : List.copyOf(frames);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.VIDEO_STREAM; }

    public int frameWidth() { return frameWidth; }
    public int frameHeight() { return frameHeight; }
    public double fps() { return fps; }
    public long frameCount() { return frameCount; }
    /** 帧列表（RGBA8888）。 */
    public List<RasterImage> frames() { return frames; }
    public Map<String, String> metadata() { return metadata; }
}