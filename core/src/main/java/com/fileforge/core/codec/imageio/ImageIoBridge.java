package com.fileforge.core.codec.imageio;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.ConversionException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * RasterImage ↔ BufferedImage 桥接（JDK ImageIO 辅助工具）。
 * <p>
 * 注意：javax.imageio / java.awt 属于 Java SE 纯 Java API（规划中的 JAVALIB 来源），
 * 但 Android 无此包；Android 端后续需替换实现，本包仅服务桌面端 P1 阶段。
 */
public final class ImageIoBridge {

    private ImageIoBridge() {}

    /** BufferedImage → RasterImage（RGBA8888）。 */
    public static RasterImage toRaster(BufferedImage bi) {
        int w = bi.getWidth(), h = bi.getHeight();
        byte[] px = new byte[w * h * 4];
        int[] argb = bi.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < argb.length; i++) {
            int v = argb[i], d = i * 4;
            px[d]     = (byte) (v >>> 16); // R
            px[d + 1] = (byte) (v >>> 8);  // G
            px[d + 2] = (byte) v;          // B
            px[d + 3] = (byte) (v >>> 24); // A
        }
        return new RasterImage(w, h, px, Map.of());
    }

    /** RasterImage → BufferedImage。opaque 时输出 RGB（用于 JPEG）。 */
    public static BufferedImage toBuffered(RasterImage img, boolean opaque) {
        int w = img.width(), h = img.height();
        BufferedImage bi = new BufferedImage(w, h,
                opaque ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        byte[] px = img.pixels();
        int[] argb = new int[w * h];
        for (int i = 0; i < argb.length; i++) {
            int s = i * 4;
            argb[i] = ((px[s + 3] & 0xFF) << 24) | ((px[s] & 0xFF) << 16)
                    | ((px[s + 1] & 0xFF) << 8) | (px[s + 2] & 0xFF);
        }
        bi.setRGB(0, 0, w, h, argb, 0, w);
        return bi;
    }

    /** 用 ImageIO 解码（取首帧，GIF 动画仅保留第一帧）。 */
    public static RasterImage decode(InputStream in, String formatName) throws ConversionException {
        try {
            BufferedImage bi = ImageIO.read(in);
            if (bi == null) throw new ConversionException("无法解码 " + formatName + " 图像");
            return toRaster(bi);
        } catch (java.io.IOException | RuntimeException e) {
            throw new ConversionException(formatName + " 解码失败: " + e.getMessage(), e);
        }
    }

    /** 用 ImageIO 编码。 */
    public static void encode(RasterImage img, OutputStream out, String formatName,
                              boolean opaque) throws ConversionException {
        try {
            if (!ImageIO.write(toBuffered(img, opaque), formatName, out)) {
                throw new ConversionException("当前环境无 " + formatName + " 编码器");
            }
        } catch (java.io.IOException | RuntimeException e) {
            throw new ConversionException(formatName + " 编码失败: " + e.getMessage(), e);
        }
    }
}
