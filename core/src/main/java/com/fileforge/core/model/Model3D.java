package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 3D 模型中间模型：网格 + 材质 + 节点层级（简化）。
 */
public final class Model3D implements Model {
    private final List<Mesh> meshes;
    private final List<Material> materials;
    private final List<Node> nodes;
    private final Map<String, String> metadata;

    public Model3D(List<Mesh> meshes, List<Material> materials, List<Node> nodes, Map<String, String> metadata) {
        this.meshes = meshes == null ? List.of() : List.copyOf(meshes);
        this.materials = materials == null ? List.of() : List.copyOf(materials);
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; } // 近似归类

    public List<Mesh> meshes() { return meshes; }
    public List<Material> materials() { return materials; }
    public List<Node> nodes() { return nodes; }
    public Map<String, String> metadata() { return metadata; }

    public static final class Mesh {
        private final String name;
        private final float[] positions;  // x,y,z * vertexCount
        private final float[] normals;
        private final float[] texCoords;  // u,v * vertexCount
        private final int[] indices;      // 三角形索引
        private final int materialIndex;

        public Mesh(String name, float[] positions, float[] normals, float[] texCoords,
                    int[] indices, int materialIndex) {
            this.name = name;
            this.positions = positions;
            this.normals = normals;
            this.texCoords = texCoords;
            this.indices = indices;
            this.materialIndex = materialIndex;
        }
        public String name() { return name; }
        public float[] positions() { return positions; }
        public float[] normals() { return normals; }
        public float[] texCoords() { return texCoords; }
        public int[] indices() { return indices; }
        public int materialIndex() { return materialIndex; }
    }

    public static final class Material {
        private final String name;
        private final float[] baseColor;      // RGBA
        private final float metallic;
        private final float roughness;
        private final String diffuseTexture;  // 相对路径或 data URI

        public Material(String name, float[] baseColor, float metallic, float roughness, String diffuseTexture) {
            this.name = name;
            this.baseColor = baseColor;
            this.metallic = metallic;
            this.roughness = roughness;
            this.diffuseTexture = diffuseTexture;
        }
        public String name() { return name; }
        public float[] baseColor() { return baseColor; }
        public float metallic() { return metallic; }
        public float roughness() { return roughness; }
        public String diffuseTexture() { return diffuseTexture; }
    }

    public static final class Node {
        private final String name;
        private final float[] translation;  // x,y,z
        private final float[] rotation;     // quaternion x,y,z,w
        private final float[] scale;        // x,y,z
        private final int meshIndex;        // -1 表示无网格
        private final List<Integer> children;

        public Node(String name, float[] translation, float[] rotation, float[] scale,
                    int meshIndex, List<Integer> children) {
            this.name = name;
            this.translation = translation;
            this.rotation = rotation;
            this.scale = scale;
            this.meshIndex = meshIndex;
            this.children = children == null ? List.of() : List.copyOf(children);
        }
        public String name() { return name; }
        public float[] translation() { return translation; }
        public float[] rotation() { return rotation; }
        public float[] scale() { return scale; }
        public int meshIndex() { return meshIndex; }
        public List<Integer> children() { return children; }
    }
}