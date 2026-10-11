package com.fileforge.core.codec.audio;

import com.fileforge.core.model.AudioStream;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;

/**
 * 音频插件（纯 Java javax.sound.sampled）：
 * - 支持 WAV/AIFF/AU（PCM 无压缩）
 * - 可选 native 扩展（MP3/FLAC/OGG 等）通过 NativeBridge
 * 仅解码为 AudioStream；编码暂支持 WAV（PCM_SIGNED 16/24/32 bit）。
 */
public final class AudioPlugin implements FormatPlugin {

    @Override public String id() { return "audio.codec"; }
    @Override public FormatFamily family() { return FormatFamily.AUDIO_STREAM; }
    @Override public Set<String> extensions() { return Set.of("wav", "aiff", "au"); }
    @Override public String mimeType() { return "audio/wav"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // RIFF/WAVE
        if (header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F') return true;
        // AIFF: "FORM" + "AIFF"
        if (header[0] == 'F' && header[1] == 'O' && header[2] == 'R' && header[3] == 'M') {
            if (header.length >= 12 && header[8] == 'A' && header[9] == 'I' && header[10] == 'F' && header[11] == 'F') return true;
        }
        // AU: ".snd" (0x2E 0x73 0x6E 0x64)
        if (header.length >= 4 && header[0] == '.' && header[1] == 's' && header[2] == 'n' && header[3] == 'd') return true;
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            AudioInputStream ais = AudioSystem.getAudioInputStream(in);
            AudioFormat fmt = ais.getFormat();

            // 转为统一 PCM_FLOAT
            AudioFormat targetFmt = new AudioFormat(AudioFormat.Encoding.PCM_FLOAT,
                    fmt.getSampleRate(), 32, fmt.getChannels(), fmt.getChannels() * 4,
                    fmt.getSampleRate(), false);
            AudioInputStream converted = AudioSystem.getAudioInputStream(targetFmt, ais);

            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = converted.read(buffer)) != -1) buf.write(buffer, 0, read);
            float[] samples = bytesToFloats(buf.toByteArray());

            pm.progress(90);
            java.util.Map<String, String> meta = new java.util.HashMap<>();
            meta.put("format", fmt.toString());
            int sr = (int) fmt.getSampleRate();
            int ch = fmt.getChannels();
            return new AudioStream(sr, ch, samples, meta);
        } catch (UnsupportedAudioFileException e) {
            throw new ConversionException("音频格式不支持（需 native 或转码）: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new ConversionException("音频读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return AudioStream.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof AudioStream audio)) {
            throw new ConversionException("AudioPlugin 仅支持 AudioStream 编码");
        }
        try (OutputStream out = sink.openStream()) {
            AudioFormat fmt = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    audio.sampleRate(), 16, audio.channels(),
                    audio.channels() * 2, audio.sampleRate(), false);
            byte[] pcm16 = floatsToBytes16(audio.samples());
            AudioInputStream ais = new AudioInputStream(new ByteArrayInputStream(pcm16), fmt, audio.samples().length / audio.channels());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, out);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("音频写入失败: " + e.getMessage(), e);
        }
    }

    // float[] (normalized -1..1) -> byte[] PCM16 LE
    private static byte[] floatsToBytes16(float[] samples) {
        byte[] out = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            int v = (int) Math.max(-32768, Math.min(32767, Math.round(samples[i] * 32767f)));
            out[i * 2] = (byte) (v & 0xFF);
            out[i * 2 + 1] = (byte) ((v >> 8) & 0xFF);
        }
        return out;
    }

    // byte[] PCM_FLOAT LE -> float[]
    private static float[] bytesToFloats(byte[] data) {
        int n = data.length / 4;
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            int b0 = data[i * 4] & 0xFF;
            int b1 = data[i * 4 + 1] & 0xFF;
            int b2 = data[i * 4 + 2] & 0xFF;
            int b3 = data[i * 4 + 3] & 0xFF;
            int bits = b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
            out[i] = Float.intBitsToFloat(bits);
        }
        return out;
    }
}