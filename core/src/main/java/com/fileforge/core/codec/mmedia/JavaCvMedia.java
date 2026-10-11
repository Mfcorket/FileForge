package com.fileforge.core.codec.mmedia;

import com.fileforge.core.model.AudioStream;
import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.ProgressMonitor;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JavaCV/FFmpeg 音视频解码工具（纯 jar 分发，无需系统安装 FFmpeg）。
 * <p>
 * 说明：JavaCV 类仅在方法体内创建（无静态链接依赖），
 * classpath 缺少 natives 时插件可加载、调用时抛 ConversionException。
 */
public final class JavaCvMedia {

    private JavaCvMedia() {}

    /** 从 InputSource 提取临时文件供 FFmpegFrameGrabber 使用。 */
    public static Path writeTemp(byte[] data, String suffix) throws ConversionException {
        try {
            Path tmp = Files.createTempFile("fileforge-", suffix);
            Files.write(tmp, data);
            return tmp;
        } catch (IOException e) {
            throw new ConversionException("临时文件创建失败: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------ 音频

    /** 解码音频文件为 AudioStream（PCM float 交织）。 */
    public static AudioStream decodeAudio(byte[] data, String suffix, ProgressMonitor pm) throws ConversionException {
        Path tmp = writeTemp(data, suffix);
        try {
            try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(tmp.toFile())) {
                g.start();
                int sampleRate = g.getSampleRate();
                int channels = g.getAudioChannels();
                if (channels <= 0) throw new ConversionException("无音频流");
                if (sampleRate <= 0) sampleRate = 44100;

                List<Float> acc = new ArrayList<>(1 << 16);
                Frame f;
                while ((f = g.grabSamples()) != null) {
                    if (f.samples == null) continue;
                    for (Buffer b : f.samples) {
                        appendSamples(acc, b);
                    }
                }
                g.stop();
                if (acc.isEmpty()) throw new ConversionException("音频解码为空");

                float[] samples = new float[acc.size()];
                for (int i = 0; i < samples.length; i++) samples[i] = acc.get(i);

                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("duration", String.format("%.2fs", (double) samples.length / channels / sampleRate));
                pm.progress(90);
                return new AudioStream(sampleRate, channels, samples, meta);
            }
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            throw new ConversionException("JavaCV 原生库未加载（需 ffmpeg natives）: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ConversionException("音频解码失败: " + e.getMessage(), e);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    // ------------------------------------------------------------ 音频编码

    // ------------------------------------------------------------ 视频编码

    /**
     * 编码帧序列（VideoStream.frames）为视频文件。
     * @param frames  RGBA8888 帧列表
     * @param fps     帧率
     * @param sink    输出
     * @param format  "mp4" / "mkv" / "webm" / "mov"
     */
    public static void encodeVideo(com.fileforge.core.model.VideoStream video, java.io.OutputStream sink,
                                   String format, String suffixPath) throws ConversionException {
        if (video.frames().isEmpty()) throw new ConversionException("视频无帧可编码");
        RasterImage first = video.frames().get(0);
        Path tmp = null;
        try {
            tmp = Files.createTempFile("fileforge-out-", suffixPath);
            try (FFmpegFrameRecorder r = new FFmpegFrameRecorder(tmp.toFile(), first.width(), first.height());
                 Java2DFrameConverter conv = new Java2DFrameConverter()) {
                r.setFormat(format);
                r.setVideoCodec(13); // AV_CODEC_ID_MPEG4
                r.setFrameRate(video.fps() > 0 ? video.fps() : 25.0);
                r.setPixelFormat(org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_YUV420P);
                r.start();
                int i = 0;
                for (RasterImage frame : video.frames()) {
                    BufferedImage bi = rasterToBuffered(frame);
                    r.record(conv.convert(bi));
                    if (++i % 10 == 0 && i < video.frames().size()) {
                        // 进度回调走 pm？此处无 pm 参数，由调用方控制
                    }
                }
                r.stop();
            }
            try (java.io.InputStream is = Files.newInputStream(tmp)) {
                is.transferTo(sink);
            }
        } catch (IOException | RuntimeException e) {
            throw new ConversionException("视频编码失败: " + e.getMessage(), e);
        } finally {
            try { if (tmp != null) Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private static BufferedImage rasterToBuffered(RasterImage img) {
        int w = img.width(), h = img.height();
        BufferedImage bi = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        byte[] px = img.pixels();
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            int s = i * 4;
            argb[i] = ((px[s] & 0xFF) << 16) | ((px[s + 1] & 0xFF) << 8) | (px[s + 2] & 0xFF);
        }
        bi.setRGB(0, 0, w, h, argb, 0, w);
        return bi;
    }

    /** 编码 AudioStream 到 OutputStream（FFmpegFrameRecorder 写临时文件再拷贝）。 */
    public static void encodeAudio(com.fileforge.core.model.AudioStream audio, java.io.OutputStream sink,
                                   String format, String suffixPath) throws ConversionException {
        Path tmp = null;
        try {
            tmp = Files.createTempFile("fileforge-out-", suffixPath);
            try (org.bytedeco.javacv.FFmpegFrameRecorder r =
                         new org.bytedeco.javacv.FFmpegFrameRecorder(tmp.toFile(), 0)) {
                r.setFormat(format);
                r.setAudioCodec(format.equals("flac") ? 139 /* AV_CODEC_ID_FLAC */ : 1 /* AV_CODEC_ID_PCM_S16LE */);
                r.setSampleRate(audio.sampleRate());
                r.setAudioChannels(audio.channels());
                r.start();

                Frame af = new Frame();
                af.sampleRate = audio.sampleRate();
                af.audioChannels = audio.channels();
                float[] src = audio.samples();
                ByteBuffer bb = ByteBuffer.allocateDirect(src.length * 2)
                        .order(java.nio.ByteOrder.nativeOrder());
                for (float v : src) {
                    bb.putShort((short) Math.max(-32768, Math.min(32767, Math.round(v * 32767f))));
                }
                bb.flip();
                af.samples = new Buffer[]{bb};
                r.record(af);
                r.stop();
            }
            try (java.io.InputStream is = Files.newInputStream(tmp)) {
                is.transferTo(sink);
            }
        } catch (IOException | RuntimeException e) {
            throw new ConversionException("音频编码失败: " + e.getMessage(), e);
        } finally {
            try { if (tmp != null) Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private static void appendSamples(List<Float> acc, Buffer buf) {
        if (buf instanceof FloatBuffer) {
            FloatBuffer fb = (FloatBuffer) buf;
            while (fb.hasRemaining()) acc.add(fb.get());
        } else if (buf instanceof ShortBuffer) {
            ShortBuffer sb = (ShortBuffer) buf;
            while (sb.hasRemaining()) acc.add(sb.get() / 32768.0f);
        } else if (buf instanceof ByteBuffer) {
            ByteBuffer bb = (ByteBuffer) buf;
            while (bb.hasRemaining()) acc.add((bb.get() - 128) / 128.0f);
        }
        // IntBuffer / LongBuffer 等未覆盖类型：忽略（FFmpegGrabber 默认 Short/Float）
    }

    // ------------------------------------------------------------ 视频

    /**
     * 解码视频为 VideoStream（RGBA 帧列表）。
     * <p>
     * 出于内存考虑 maxFrames 限制帧数；若实际帧数超过则均匀抽样
     * （首帧始终保留，供预览）。
     */
    public static com.fileforge.core.model.VideoStream decodeVideo(byte[] data, String suffix, int maxFrames,
                                                                   ProgressMonitor pm) throws ConversionException {
        Path tmp = writeTemp(data, suffix);
        try {
            try (FFmpegFrameGrabber g = new FFmpegFrameGrabber(tmp.toFile());
                 Java2DFrameConverter conv = new Java2DFrameConverter()) {
                g.start();
                int width = g.getImageWidth();
                int height = g.getImageHeight();
                double fps = g.getFrameRate() > 0 ? g.getFrameRate() : 25.0;
                long total = g.getLengthInFrames();
                if (width <= 0 || height <= 0) throw new ConversionException("无视频流");

                List<RasterImage> frames = new ArrayList<>();
                long effective = total > 0 ? total : 0;
                Frame f;
                long idx = 0;
                while ((f = g.grabImage()) != null) {
                    long cur = idx++;
                    boolean keep = frames.isEmpty() || cur % Math.max(1, (long) (effective / maxFrames)) == 0
                            || (effective > 0 && cur == effective - 1);
                    if (keep && frames.size() < maxFrames) {
                        RasterImage ri = bufferedToRaster(f, conv);
                        if (ri != null) frames.add(ri);
                    }
                    if (frames.size() >= maxFrames) break;
                    if (idx % 10 == 0) pm.progress((int) Math.min(90, idx * 90.0 / Math.max(1, effective)));
                }
                g.stop();
                if (frames.isEmpty()) throw new ConversionException("未解码到视频帧");

                Map<String, String> meta = new LinkedHashMap<>();
                meta.put("decodedFrames", String.valueOf(frames.size()));
                meta.put("totalFrames", String.valueOf(effective));
                pm.progress(95);
                return new com.fileforge.core.model.VideoStream(width, height, fps, effective, frames, meta);
            }
        } catch (UnsatisfiedLinkError | ExceptionInInitializerError e) {
            throw new ConversionException("JavaCV 原生库未加载（需 ffmpeg natives）: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ConversionException("视频解码失败: " + e.getMessage(), e);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    private static RasterImage bufferedToRaster(Frame f, Java2DFrameConverter conv) {
        BufferedImage bi = conv.convert(f);
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
        return new RasterImage(w, h, px, Map.of());
    }
}