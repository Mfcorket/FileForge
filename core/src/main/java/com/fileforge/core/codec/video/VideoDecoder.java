package com.fileforge.core.codec.video;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.ConversionException;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 视频解码器：提取首帧或解码为帧序列（JavaCV / FFmpeg）。
 */
public final class VideoDecoder {

    private VideoDecoder() {}

    /**
     * 从视频文件提取首帧为 RasterImage。
     * @param videoPath 视频文件路径
     * @return 首帧 RasterImage，若无帧或解码失败返回 null
     */
    public static RasterImage decodeFirstFrame(Path videoPath) throws ConversionException {
        try (FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(videoPath.toFile())) {
            grabber.start();
            Frame frame = grabber.grabImage();
            grabber.stop();
            if (frame == null || frame.image == null) return null;
            return toRaster(frame);
        } catch (Exception e) {
            throw new ConversionException("视频首帧提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 InputStream 解码首帧（需写临时文件，FFmpegFrameGrabber 需文件路径）。
     */
    public static RasterImage decodeFirstFrame(InputStream in) throws ConversionException {
        // 写临时文件，FFmpegFrameGrabber 需文件路径
        try {
            Path tmp = Files.createTempFile("fileforge-video-", ".tmp");
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return decodeFirstFrame(tmp);
        } catch (Exception e) {
            throw new ConversionException("视频首帧读取失败: " + e.getMessage(), e);
        }
    }

    private static RasterImage toRaster(Frame frame) {
        BufferedImage bi = new Java2DFrameConverter().convert(frame);
        if (bi == null) return null;
        int w = bi.getWidth(), h = bi.getHeight();
        byte[] px = new byte[w * h * 4];
        int[] argb = bi.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < argb.length; i++) {
            int v = argb[i], d = i * 4;
            px[d]     = (byte) (v >>> 16);
            px[d + 1] = (byte) (v >>> 8);
            px[d + 2] = (byte) v;
            px[d + 3] = (byte) (v >>> 24);
        }
        return new com.fileforge.core.model.RasterImage(w, h, px, Map.of());
    }
}