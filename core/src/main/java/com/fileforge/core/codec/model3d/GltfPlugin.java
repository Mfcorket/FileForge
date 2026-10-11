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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GLTF 插件（GL Transmission Format）：JSON + 二进制缓冲。
 * 仅解码基础网格/材质/节点层级为 Model3D（编码暂不支持）。
 */
public final class GltfPlugin implements FormatPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override public String id() { return "gltf.codec"; }
    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; }
    @Override public Set<String> extensions() { return Set.of("gltf", "glb"); }
    @Override public String mimeType() { return "model/gltf+json"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // glb: "gltf" magic (0x676C7466)
        if ((header[0] & 0xFF) == 0x67 && (header[1] & 0xFF) == 0x6C
                && (header[2] & 0xFF) == 0x74 && (header[3] & 0xFF) == 0x66) return true;
        // gltf: JSON 以 { 开头且包含 "asset"
        String s = new String(header, StandardCharsets.US_ASCII).trim();
        return s.startsWith("{") && s.contains("\"asset\"");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            JsonNode root;
            byte[] binChunk = null;

            if (data.length >= 12 && (data[0] & 0xFF) == 0x67 && (data[1] & 0xFF) == 0x6C
                    && (data[2] & 0xFF) == 0x74 && (data[3] & 0xFF) == 0x66) {
                // GLB 二进制容器
                int version = ByteBuffer.wrap(data, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int length = ByteBuffer.wrap(data, 8, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int offset = 12;
                // 第一个 chunk: JSON
                int chunkLen = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                int chunkType = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                if (chunkType == 0x4E4F534A) { // "JSON"
                    String jsonStr = new String(data, offset + 8, chunkLen, StandardCharsets.UTF_8);
                    root = MAPPER.readTree(jsonStr);
                    offset += 8 + chunkLen;
                    // 第二个 chunk: BIN
                    if (offset + 8 <= data.length) {
                        int binLen = ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        int binType = ByteBuffer.wrap(data, offset + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
                        if (binType == 0x004E4942) { // "BIN"
                            binChunk = new byte[binLen];
                            System.arraycopy(data, offset + 8, binChunk, 0, binLen);
                        }
                    }
                } else {
                    throw new ConversionException("GLB 第一个 chunk 非 JSON");
                }
            } else {
                root = MAPPER.readTree(data);
            }

            List<Model3D.Mesh> meshes = new ArrayList<>();
            List<Model3D.Material> materials = new ArrayList<>();
            List<Model3D.Node> nodes = new ArrayList<>();

            // 解析材质
            if (root.has("materials")) {
                for (JsonNode mat : root.get("materials")) {
                    JsonNode pbr = mat.has("pbrMetallicRoughness") ? mat.get("pbrMetallicRoughness") : null;
                    float[] baseColor = {1,1,1,1};
                    float metallic = 1, roughness = 1;
                    String texPath = "";
                    if (pbr != null) {
                        if (pbr.has("baseColorFactor")) {
                            JsonNode arr = pbr.get("baseColorFactor");
                            baseColor = new float[]{arr.get(0).floatValue(), arr.get(1).floatValue(),
                                    arr.get(2).floatValue(), arr.get(3).floatValue()};
                        }
                        metallic = (float) pbr.path("metallicFactor").asDouble(1.0);
                        roughness = (float) pbr.path("roughnessFactor").asDouble(1.0);
                        if (pbr.has("baseColorTexture")) {
                            int texIdx = pbr.get("baseColorTexture").path("index").asInt(-1);
                            if (texIdx >= 0 && root.has("textures")) {
                                JsonNode tex = root.get("textures").get(texIdx);
                                int imgIdx = tex.path("source").asInt(-1);
                                if (imgIdx >= 0 && root.has("images")) {
                                    texPath = root.get("images").get(imgIdx).path("uri").asText("");
                                }
                            }
                        }
                    }
                    materials.add(new Model3D.Material(
                            mat.path("name").asText("mat"), baseColor, metallic, roughness, texPath));
                }
            }

            // 解析网格
            if (root.has("meshes")) {
                for (JsonNode meshNode : root.get("meshes")) {
                    String meshName = meshNode.path("name").asText("mesh");
                    if (meshNode.has("primitives")) {
                        for (JsonNode prim : meshNode.get("primitives")) {
                            // 属性访问器
                            JsonNode attrs = prim.get("attributes");
                            JsonNode posAcc = attrs.has("POSITION") ? attrs.get("POSITION") : null;
                            JsonNode norAcc = attrs.has("NORMAL") ? attrs.get("NORMAL") : null;
                            JsonNode texAcc = attrs.has("TEXCOORD_0") ? attrs.get("TEXCOORD_0") : null;
                            JsonNode idxAcc = prim.has("indices") ? root.get("accessors").get(prim.get("indices").asInt()) : null;

                            if (posAcc != null) {
                                float[] positions = readAccessor(root, posAcc, binChunk);
                                float[] normals = norAcc != null ? readAccessor(root, norAcc, binChunk) : new float[positions.length];
                                float[] texCoords = texAcc != null ? readAccessor(root, texAcc, binChunk) : new float[0];
                                int[] indices = idxAcc != null ? readIndices(root, idxAcc, binChunk) : buildSequentialIndices(positions.length / 3);
                                int matIdx = prim.path("material").asInt(-1);
                                meshes.add(new Model3D.Mesh(meshName, positions, normals, texCoords, indices, matIdx));
                            }
                        }
                    }
                }
            }

            // 解析节点层级
            if (root.has("nodes")) {
                for (JsonNode n : root.get("nodes")) {
                    float[] trans = {0,0,0}, rot = {0,0,0,1}, scale = {1,1,1};
                    if (n.has("translation")) {
                        JsonNode t = n.get("translation");
                        trans = new float[]{t.get(0).floatValue(), t.get(1).floatValue(), t.get(2).floatValue()};
                    }
                    if (n.has("rotation")) {
                        JsonNode r = n.get("rotation");
                        rot = new float[]{r.get(0).floatValue(), r.get(1).floatValue(), r.get(2).floatValue(), r.get(3).floatValue()};
                    }
                    if (n.has("scale")) {
                        JsonNode s = n.get("scale");
                        scale = new float[]{s.get(0).floatValue(), s.get(1).floatValue(), s.get(2).floatValue()};
                    }
                    int meshIdx = n.path("mesh").asInt(-1);
                    List<Integer> children = new ArrayList<>();
                    if (n.has("children")) n.get("children").forEach(c -> children.add(c.asInt()));
                    nodes.add(new Model3D.Node(n.path("name").asText("node"), trans, rot, scale, meshIdx, children));
                }
            }

            return new Model3D(meshes, materials, nodes, Map.of("gltfVersion", root.path("asset").path("version").asText("2.0")));
        } catch (IOException e) {
            throw new ConversionException("GLTF 解析失败: " + e.getMessage(), e);
        }
    }

    private float[] readAccessor(JsonNode root, JsonNode acc, byte[] binChunk) {
        int bufferViewIdx = acc.path("bufferView").asInt(-1);
        int byteOffset = acc.path("byteOffset").asInt(0);
        int count = acc.path("count").asInt(0);
        int componentType = acc.path("componentType").asInt(5126); // 5126=FLOAT
        String type = acc.path("type").asText("VEC3");
        int components = switch (type) { case "SCALAR" -> 1; case "VEC2" -> 2; case "VEC3" -> 3; case "VEC4" -> 4; default -> 3; };

        byte[] bufferData;
        if (bufferViewIdx >= 0 && root.has("bufferViews")) {
            JsonNode bv = root.get("bufferViews").get(bufferViewIdx);
            int bvOffset = bv.path("byteOffset").asInt(0);
            int bvLength = bv.path("byteLength").asInt(0);
            int bufferIdx = bv.path("buffer").asInt(0);
            if (binChunk != null) {
                bufferData = binChunk;
            } else if (root.has("buffers")) {
                JsonNode buf = root.get("buffers").get(bufferIdx);
                String uri = buf.path("uri").asText("");
                if (uri.startsWith("data:")) {
                    String base64 = uri.substring(uri.indexOf(",") + 1);
                    bufferData = Base64.getDecoder().decode(base64);
                } else {
                    bufferData = new byte[0]; // 外部文件暂不支持
                }
            } else {
                bufferData = new byte[0];
            }
        } else {
            bufferData = binChunk != null ? binChunk : new byte[0];
        }

        ByteBuffer bb = ByteBuffer.wrap(bufferData).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(byteOffset);
        float[] result = new float[count * components];
        for (int i = 0; i < count * components; i++) {
            if (componentType == 5126) result[i] = bb.getFloat();
            else if (componentType == 5123) result[i] = bb.getShort() & 0xFFFF; // UNSIGNED_SHORT
            else if (componentType == 5125) result[i] = bb.getInt(); // UNSIGNED_INT
            else result[i] = 0;
        }
        return result;
    }

    private int[] readIndices(JsonNode root, JsonNode acc, byte[] binChunk) {
        float[] raw = readAccessor(root, acc, binChunk);
        int[] idx = new int[raw.length];
        for (int i = 0; i < raw.length; i++) idx[i] = (int) raw[i];
        return idx;
    }

    private int[] buildSequentialIndices(int vertexCount) {
        int[] idx = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) idx[i] = i;
        return idx;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) { return false; }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("GLTF 写入暂不支持");
    }
}