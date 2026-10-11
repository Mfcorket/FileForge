package com.fileforge.core.codec.png;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * 自研 PNG 编解码器（无外部依赖，仅 java.util.zip）。
 * 解码：8 位、非隔行，颜色类型 0/2/3/4/6，滤镜 0~4。
 * 编码：颜色类型 6（RGBA8888），滤镜 0。
 */
public final class PngCodec {

    public static final byte[] SIGNATURE =
            {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private PngCodec() {}

    public static boolean matches(byte[] header) {
        if (header == null || header.length < SIGNATURE.length) return false;
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (header[i] != SIGNATURE[i]) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- decode

    public static RasterImage decode(InputStream in, ProgressMonitor pm) throws ConversionException {
        try {
            byte[] sig = in.readNBytes(8);
            if (!matches(sig)) throw new ConversionException("不是有效的 PNG 文件（签名不匹配）");

            int width = 0, height = 0, bitDepth = 0, colorType = 0, interlace = 0;
            byte[] palette = null;
            byte[] trns = null;
            ByteArrayOutputStream idat = new ByteArrayOutputStream();
            Map<String, String> meta = new HashMap<>();

            boolean sawIend = false;
            while (!sawIend) {
                byte[] lenBuf = in.readNBytes(4);
                if (lenBuf.length < 4) throw new EOFException("PNG 数据被截断");
                int length = readInt(lenBuf, 0);
                if (length < 0 || length > 1 << 26) {
                    throw new ConversionException("非法 PNG 块长度: " + length);
                }
                byte[] typeBuf = in.readNBytes(4);
                if (typeBuf.length < 4) throw new EOFException("PNG 数据被截断");
                String type = new String(typeBuf, java.nio.charset.StandardCharsets.US_ASCII);
                byte[] data = in.readNBytes(length);
                if (data.length < length) throw new EOFException("PNG 数据被截断");
                in.skip(4); // CRC

                switch (type) {
                    case "IHDR" -> {
                        width = readInt(data, 0);
                        height = readInt(data, 4);
                        bitDepth = data[8] & 0xFF;
                        colorType = data[9] & 0xFF;
                        interlace = data[12] & 0xFF;
                        if (bitDepth != 8) throw new ConversionException("暂不支持位深 " + bitDepth + " 的 PNG");
                        if (interlace != 0) throw new ConversionException("暂不支持隔行扫描 PNG");
                    }
                    case "PLTE" -> palette = data;
                    case "tRNS" -> trns = data;
                    case "IDAT" -> idat.write(data);
                    case "tEXt" -> {
                        int nul = indexOf(data, (byte) 0);
                        if (nul > 0) {
                            meta.put(new String(data, 0, nul, java.nio.charset.StandardCharsets.ISO_8859_1),
                                     new String(data, nul + 1, data.length - nul - 1, java.nio.charset.StandardCharsets.ISO_8859_1));
                        }
                    }
                    case "IEND" -> sawIend = true;
                    default -> { /* 跳过未知辅助块 */ }
                }
            }
            if (width <= 0 || height <= 0) throw new ConversionException("缺少 IHDR");

            int channels = switch (colorType) {
                case 0 -> 1; case 2 -> 3; case 3 -> 1; case 4 -> 2; case 6 -> 4;
                default -> throw new ConversionException("不支持的 PNG 颜色类型: " + colorType);
            };

            byte[] raw = inflate(idat.toByteArray(), (width * channels + 1) * height);
            byte[] rgba = unfilter(raw, width, height, channels, pm);

            // 统一转为 RGBA8888
            byte[] out = new byte[width * height * 4];
            for (int i = 0; i < width * height; i++) {
                int s = i * channels, d = i * 4;
                switch (colorType) {
                    case 0 -> { byte g = rgba[s]; out[d] = g; out[d+1] = g; out[d+2] = g; out[d+3] = (byte) 0xFF; }
                    case 2 -> { out[d] = rgba[s]; out[d+1] = rgba[s+1]; out[d+2] = rgba[s+2]; out[d+3] = (byte) 0xFF; }
                    case 3 -> {
                        int idx = rgba[s] & 0xFF;
                        if (palette == null || idx * 3 + 2 >= palette.length)
                            throw new ConversionException("调色板索引越界");
                        out[d] = palette[idx*3]; out[d+1] = palette[idx*3+1]; out[d+2] = palette[idx*3+2];
                        out[d+3] = (trns != null && idx < trns.length) ? trns[idx] : (byte) 0xFF;
                    }
                    case 4 -> { byte g = rgba[s]; out[d] = g; out[d+1] = g; out[d+2] = g; out[d+3] = rgba[s+1]; }
                    case 6 -> System.arraycopy(rgba, s, out, d, 4);
                }
            }
            return new RasterImage(width, height, out, meta);
        } catch (IOException | DataFormatException e) {
            throw new ConversionException("PNG 解码失败", e);
        } catch (RuntimeException e) {
            throw new ConversionException("PNG 数据损坏: " + e.getMessage(), e);
        }
    }

    private static byte[] inflate(byte[] compressed, int expectedSize) throws DataFormatException {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        byte[] out = new byte[expectedSize];
        int n = inflater.inflate(out);
        inflater.end();
        if (n != expectedSize) throw new DataFormatException("解压长度不符: " + n + " != " + expectedSize);
        return out;
    }

    /** 还原 PNG 滤镜（0~4），逐行处理。 */
    private static byte[] unfilter(byte[] raw, int width, int height, int channels, ProgressMonitor pm) {
        int stride = width * channels;
        byte[] out = new byte[stride * height];
        int pos = 0;
        for (int y = 0; y < height; y++) {
            int filter = raw[pos++] & 0xFF;
            int rowOff = y * stride, prevOff = (y - 1) * stride;
            for (int x = 0; x < stride; x++) {
                int rawB = raw[pos++] & 0xFF;
                int a = x >= channels ? out[rowOff + x - channels] & 0xFF : 0;
                int b = y > 0 ? out[prevOff + x] & 0xFF : 0;
                int c = (x >= channels && y > 0) ? out[prevOff + x - channels] & 0xFF : 0;
                int val = switch (filter) {
                    case 0 -> rawB;
                    case 1 -> rawB + a;
                    case 2 -> rawB + b;
                    case 3 -> rawB + (a + b) / 2;
                    case 4 -> rawB + paeth(a, b, c);
                    default -> throw new IllegalStateException("未知 PNG 滤镜: " + filter);
                };
                out[rowOff + x] = (byte) val;
            }
            if (y % 64 == 0) pm.progress((int) ((long) y * 100 / height));
        }
        return out;
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c;
        int pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        if (pa <= pb && pa <= pc) return a;
        if (pb <= pc) return b;
        return c;
    }

    // ---------------------------------------------------------------- encode

    public static void encode(RasterImage image, OutputStream out, ProgressMonitor pm) throws ConversionException {
        try {
            int width = image.width(), height = image.height();
            byte[] pixels = image.pixels();

            out.write(SIGNATURE);

            // IHDR
            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            writeInt(ihdr, width);
            writeInt(ihdr, height);
            ihdr.write(8);  // bit depth
            ihdr.write(6);  // color type RGBA
            ihdr.write(0);  // compression
            ihdr.write(0);  // filter
            ihdr.write(0);  // no interlace
            writeChunk(out, "IHDR", ihdr.toByteArray());

            // IDAT：滤镜 0，每行前加 0 字节
            byte[] raw = new byte[(width * 4 + 1) * height];
            for (int y = 0; y < height; y++) {
                raw[y * (width * 4 + 1)] = 0;
                System.arraycopy(pixels, y * width * 4, raw, y * (width * 4 + 1) + 1, width * 4);
                if (y % 64 == 0) pm.progress((int) ((long) y * 100 / height));
            }
            Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION);
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            while (!deflater.finished()) {
                int n = deflater.deflate(buf);
                compressed.write(buf, 0, n);
            }
            deflater.end();
            writeChunk(out, "IDAT", compressed.toByteArray());

            writeChunk(out, "IEND", new byte[0]);
            out.flush();
        } catch (IOException e) {
            throw new ConversionException("PNG 编码失败", e);
        }
    }

    private static void writeChunk(OutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static int readInt(byte[] b, int off) {
        return ((b[off] & 0xFF) << 24) | ((b[off+1] & 0xFF) << 16) | ((b[off+2] & 0xFF) << 8) | (b[off+3] & 0xFF);
    }

    private static void writeInt(OutputStream out, int v) throws IOException {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }

    private static int indexOf(byte[] arr, byte target) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == target) return i;
        return -1;
    }
}
