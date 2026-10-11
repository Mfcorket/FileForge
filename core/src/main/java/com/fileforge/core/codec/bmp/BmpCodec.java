package com.fileforge.core.codec.bmp;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/**
 * 自研 BMP 编解码器（无外部依赖）。
 * 解码：24/32 位无压缩 BI_RGB，BITMAPINFOHEADER(V3) 及以上，自底向上 / 自顶向下。
 * 编码：32 位 BITMAPV3（BI_RGB），自底向上。
 */
public final class BmpCodec {

    private BmpCodec() {}

    public static boolean matches(byte[] header) {
        return header != null && header.length >= 2 && header[0] == 'B' && header[1] == 'M';
    }

    // ---------------------------------------------------------------- decode

    public static RasterImage decode(InputStream in, ProgressMonitor pm) throws ConversionException {
        try {
            byte[] all = in.readAllBytes();
            if (all.length < 54 || all[0] != 'B' || all[1] != 'M') {
                throw new ConversionException("不是有效的 BMP 文件");
            }
            int dataOffset = readIntLE(all, 10);
            int headerSize = readIntLE(all, 14);
            if (headerSize < 40) throw new ConversionException("不支持 OS/2 BMP 头");

            int width = readIntLE(all, 18);
            int heightRaw = readIntLE(all, 22);
            int planes = readShortLE(all, 26);
            int bpp = readShortLE(all, 28);
            int compression = readIntLE(all, 30);

            if (planes != 1) throw new ConversionException("非法 BMP planes: " + planes);
            if (bpp != 24 && bpp != 32) throw new ConversionException("仅支持 24/32 位 BMP，收到 " + bpp);
            if (compression != 0) throw new ConversionException("仅支持无压缩 BMP（BI_RGB）");
            if (width <= 0 || heightRaw == 0) throw new ConversionException("非法 BMP 尺寸");

            boolean topDown = heightRaw < 0;
            int height = Math.abs(heightRaw);
            int bytesPerPixel = bpp / 8;
            int stride = (width * bytesPerPixel + 3) & ~3; // 行 4 字节对齐
            if ((long) dataOffset + (long) stride * height > all.length) {
                throw new ConversionException("BMP 数据被截断");
            }

            byte[] rgba = new byte[width * height * 4];
            for (int row = 0; row < height; row++) {
                int srcRow = topDown ? row : (height - 1 - row);
                int rowOff = dataOffset + srcRow * stride;
                for (int x = 0; x < width; x++) {
                    int s = rowOff + x * bytesPerPixel;
                    int d = (row * width + x) * 4;
                    rgba[d]     = all[s + 2]; // R
                    rgba[d + 1] = all[s + 1]; // G
                    rgba[d + 2] = all[s];     // B
                    rgba[d + 3] = bytesPerPixel == 4 ? all[s + 3] : (byte) 0xFF;
                }
                if (row % 128 == 0) pm.progress((int) ((long) row * 100 / height));
            }
            return new RasterImage(width, height, rgba, Map.of());
        } catch (IOException e) {
            throw new ConversionException("BMP 读取失败", e);
        } catch (RuntimeException e) {
            throw new ConversionException("BMP 数据损坏: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- encode

    public static void encode(RasterImage image, OutputStream out, ProgressMonitor pm) throws ConversionException {
        try {
            int width = image.width(), height = image.height();
            byte[] pixels = image.pixels();
            int stride = width * 4; // 32 位天然 4 字节对齐
            int imageSize = stride * height;
            int dataOffset = 54; // BITMAPFILEHEADER(14) + BITMAPINFOHEADER(40)

            ByteArrayOutputStream header = new ByteArrayOutputStream();
            // BITMAPFILEHEADER
            header.write('B'); header.write('M');
            writeIntLE(header, dataOffset + imageSize); // 文件大小
            writeShortLE(header, 0); writeShortLE(header, 0); // reserved
            writeIntLE(header, dataOffset);
            // BITMAPINFOHEADER
            writeIntLE(header, 40);
            writeIntLE(header, width);
            writeIntLE(header, height); // 自底向上
            writeShortLE(header, 1);    // planes
            writeShortLE(header, 32);   // bpp
            writeIntLE(header, 0);      // BI_RGB
            writeIntLE(header, imageSize);
            writeIntLE(header, 2835);   // 72 DPI
            writeIntLE(header, 2835);
            writeIntLE(header, 0);
            writeIntLE(header, 0);
            out.write(header.toByteArray());

            byte[] rowBuf = new byte[stride];
            for (int row = height - 1; row >= 0; row--) {
                for (int x = 0; x < width; x++) {
                    int s = (row * width + x) * 4;
                    int d = x * 4;
                    rowBuf[d]     = pixels[s + 2]; // B
                    rowBuf[d + 1] = pixels[s + 1]; // G
                    rowBuf[d + 2] = pixels[s];     // R
                    rowBuf[d + 3] = pixels[s + 3]; // A
                }
                out.write(rowBuf);
                if (row % 128 == 0) pm.progress((int) ((long) (height - row) * 100 / height));
            }
            out.flush();
        } catch (IOException e) {
            throw new ConversionException("BMP 写入失败", e);
        }
    }

    private static int readIntLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8) | ((b[off+2] & 0xFF) << 16) | ((b[off+3] & 0xFF) << 24);
    }

    private static int readShortLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8);
    }

    private static void writeIntLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static void writeShortLE(OutputStream out, int v) throws IOException {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }
}
