package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.Map;

/**
 * 音频流中间模型：PCM 浮点采样（-1.0f .. 1.0f），多声道交织。
 */
public final class AudioStream implements Model {
    private final int sampleRate;
    private final int channels;
    private final float[] samples; // 交织：ch0, ch1, ch0, ch1...
    private final Map<String, String> metadata;

    public AudioStream(int sampleRate, int channels, float[] samples, Map<String, String> metadata) {
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate > 0");
        if (channels <= 0) throw new IllegalArgumentException("channels > 0");
        if (samples == null) throw new IllegalArgumentException("samples != null");
        if (samples.length % channels != 0) throw new IllegalArgumentException("samples length not multiple of channels");
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.samples = samples.clone();
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.AUDIO_STREAM; }

    public int sampleRate() { return sampleRate; }
    public int channels() { return channels; }
    /** 返回采样副本（交织 float[]）。 */
    public float[] samples() { return samples.clone(); }
    public int frameCount() { return samples.length / channels; }
    public Map<String, String> metadata() { return metadata; }
}