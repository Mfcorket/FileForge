package com.fileforge.core.codec.model3d;

import com.fileforge.core.model.Model3D;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * STL 插件（立体光刻）：支持 ASCII 和二进制格式，仅三角形网格。
 * 仅解码为 Model3D（编码暂不支持）。
 */
public final class StlPlugin implements FormatPlugin {

    @Override public String id() { return "stl.codec"; }
    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; }
    @Override public Set<String> extensions() { return Set.of("stl"); }
    @Override public String mimeType() { return "model/stl"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 5) return false;
        String s = new String(header, 0, Math.min(5, header.length), StandardCharsets.US_ASCII).toLowerCase();
        return s.startsWith("solid");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] first80 = in.readNBytes(80);
            boolean isAscii = new String(first80, StandardCharsets.US_ASCII).trim().toLowerCase().startsWith("solid");
            InputStream dataIn = new ByteArrayInputStream(first80); // 重置

            List<float[]> positions = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();

            if (isAscii) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII))) {
                    String line;
                    float[] currentNormal = null;
                    while ((line = br.readLine()) != null) {
                        line = line.trim();
                        if (line.startsWith("facet normal")) {
                            String[] p = line.split("\\s+");
                            currentNormal = new float[]{parseF(p[2]), parseF(p[3]), parseF(p[4])};
                        } else if (line.startsWith("vertex")) {
                            String[] p = line.split("\\s+");
                            positions.add(new float[]{parseF(p[1]), parseF(p[2]), parseF(p[3])});
                            if (currentNormal != null) normals.add(currentNormal);
                        }
                    }
                }
            } else {
                // 二进制：跳过 80 字节头 + 4 字节三角形数
                byte[] header = new byte[80];
                dataIn.read(header);
                DataInputStream dis = new DataInputStream(dataIn);
                // 这里简化：读取三角形数
                byte[] countBytes = new byte[4];
                dis.readFully(countBytes);
                int triCount = ByteBuffer.wrap(countBytes).order(ByteOrder.LITTLE_ENDIAN).getInt();
                for (int i = 0; i < triCount; i++) {
                    float[] n = readVec3(dis);
                    normals.add(n); normals.add(n); normals.add(n); // 每顶点一个法线
                    positions.add(readVec3(dis));
                    positions.add(readVec3(dis));
                    positions.add(readVec3(dis));
                    dis.readShort(); // attribute byte count
                }
            }
            int vertexCount = positions.size();
            float[] posFlat = new float[vertexCount * 3];
            for (int i = 0; i < vertexCount; i++) {
                float[] p = positions.get(i);
                posFlat[i*3] = p[0]; posFlat[i*3+1] = p[1]; posFlat[i*3+2] = p[2];
            }
            float[] norFlat = new float[vertexCount * 3];
            for (int i = 0; i < normals.size(); i++) {
                float[] n = normals.get(i);
                norFlat[i*3] = n[0]; norFlat[i*3+1] = n[1]; norFlat[i*3+2] = n[2];
            }
            int[] indices = new int[vertexCount];
            for (int i = 0; i < vertexCount; i++) indices[i] = i;

            var mesh = new Model3D.Mesh("stl", posFlat, norFlat, new float[0], indices, -1);
            var model = new Model3D(List.of(mesh), List.of(), List.of(), Map.of("triangles", String.valueOf(vertexCount / 3)));
            pm.progress(90);
            return model;
        } catch (IOException e) {
            throw new ConversionException("STL 解析失败: " + e.getMessage(), e);
        }
    }

    private static float parseF(String s) { return Float.parseFloat(s); }
    private static float[] readVec3(DataInputStream dis) throws IOException {
        return new float[]{dis.readFloat(), dis.readFloat(), dis.readFloat()};
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) { return false; }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("STL 写入暂不支持");
    }
}