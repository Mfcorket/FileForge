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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OBJ 插件（Wavefront OBJ）：纯文本格式，支持顶点/法线/纹理坐标/面。
 * 仅解码为 Model3D（编码暂不支持）。
 */
public final class ObjPlugin implements FormatPlugin {

    @Override public String id() { return "obj.codec"; }
    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; }
    @Override public Set<String> extensions() { return Set.of("obj"); }
    @Override public String mimeType() { return "model/obj"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null) return false;
        String s = new String(header).trim();
        return s.startsWith("v ") || s.startsWith("vn ") || s.startsWith("vt ") || s.startsWith("f ") || s.startsWith("#");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<float[]> positions = new ArrayList<>();
            List<float[]> normals = new ArrayList<>();
            List<float[]> texCoords = new ArrayList<>();
            List<int[]> faces = new ArrayList<>(); // 每个面 3 顶点索引 (position/texcoord/normal)

            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> {
                        if (parts.length >= 4) positions.add(new float[]{parseF(parts[1]), parseF(parts[2]), parseF(parts[3])});
                    }
                    case "vn" -> {
                        if (parts.length >= 4) normals.add(new float[]{parseF(parts[1]), parseF(parts[2]), parseF(parts[3])});
                    }
                    case "vt" -> {
                        if (parts.length >= 3) texCoords.add(new float[]{parseF(parts[1]), parseF(parts[2])});
                    }
                    case "f" -> {
                        if (parts.length >= 4) {
                            int[] face = new int[9]; // 3 顶点 * 3 分量
                            for (int i = 1; i <= 3; i++) {
                                String[] v = parts[i].split("/");
                                face[(i-1)*3] = parseIdx(v[0], positions.size());
                                face[(i-1)*3+1] = v.length > 1 && !v[1].isEmpty() ? parseIdx(v[1], texCoords.size()) : -1;
                                face[(i-1)*3+2] = v.length > 2 && !v[2].isEmpty() ? parseIdx(v[2], normals.size()) : -1;
                            }
                            faces.add(face);
                        }
                    }
                }
            }
            // 扁平化
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
            float[] texFlat = new float[texCoords.size() * 2];
            for (int i = 0; i < texCoords.size(); i++) {
                float[] t = texCoords.get(i);
                texFlat[i*2] = t[0]; texFlat[i*2+1] = t[1];
            }
            int[] idxFlat = new int[faces.size() * 9];
            for (int i = 0; i < faces.size(); i++) System.arraycopy(faces.get(i), 0, idxFlat, i*9, 9);

            var mesh = new Model3D.Mesh("obj", posFlat, norFlat, texFlat, idxFlat, -1);
            var model = new Model3D(List.of(mesh), List.of(), List.of(), Map.of("faces", String.valueOf(faces.size())));
            pm.progress(90);
            return model;
        } catch (IOException e) {
            throw new ConversionException("OBJ 解析失败: " + e.getMessage(), e);
        }
    }

    private static float parseF(String s) { return Float.parseFloat(s); }
    private static int parseIdx(String s, int size) {
        int idx = Integer.parseInt(s);
        return idx > 0 ? idx - 1 : (size + idx); // 负索引相对于末尾
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) { return false; }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("OBJ 写入暂不支持");
    }
}