package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.Map;

/**
 * 统一位图中间模型：RGBA8888 像素缓冲 + 元数据。
 * 像素按行主序存储，每像素 4 字节 R,G,B,A。
 */
public final class RasterImage implements Model {
    private final int width;
    private final int height;
    /** RGBA8888，长度 = width * height * 4。 */
    private final byte[] pixels;
    private final Map<String, String> metadata;

    public RasterImage(int width, int height, byte[] rgbaPixels, Map<String, String> metadata) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("invalid size: " + width + "x" + height);
        }
        if (rgbaPixels.length != (long) width * height * 4) {
            throw new IllegalArgumentException("pixel buffer size mismatch");
        }
        this.width = width;
        this.height = height;
        this.pixels = rgbaPixels.clone();
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override
    public FormatFamily family() {
        return FormatFamily.RASTER_IMAGE;
    }

    public int width() { return width; }

    public int height() { return height; }

    /** 返回像素缓冲副本（RGBA8888）。 */
    public byte[] pixels() { return pixels.clone(); }

    /** 读取 (x,y) 处像素到 dst[0..3]（RGBA）。 */
    public void getPixel(int x, int y, byte[] dst) {
        int i = (y * width + x) * 4;
        System.arraycopy(pixels, i, dst, 0, 4);
    }

    public Map<String, String> metadata() { return metadata; }
}
