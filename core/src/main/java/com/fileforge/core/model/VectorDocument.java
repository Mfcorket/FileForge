package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 统一矢量文档中间模型（§4.2）：SVG/.ai/PDF/EPS 的载体。
 * 包含画布尺寸、矢量元素序列（路径/矩形/圆形/文本/图片引用）、样式与元数据。
 */
public final class VectorDocument implements Model {
    private final double width;
    private final double height;
    private final List<Element> elements;
    private final Map<String, String> metadata;

    public VectorDocument(double width, double height, List<Element> elements, Map<String, String> metadata) {
        this.width = width;
        this.height = height;
        this.elements = elements == null ? List.of() : List.copyOf(elements);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; }

    public double width() { return width; }
    public double height() { return height; }
    public List<Element> elements() { return elements; }
    public Map<String, String> metadata() { return metadata; }

    /** 矢量元素基类（不可变值对象）。 */
    public sealed interface Element permits Path, Rect, Circle, Text, ImageRef {}

    /** 路径：可选填充色(ARGB int)与描边色；d 为 SVG path 语法子集。 */
    public record Path(String d, Integer fillArgb, Integer strokeArgb, Double strokeWidth) implements Element {}

    /** 矩形：中心定位 x,y 为左上角，w/h >= 0。 */
    public record Rect(double x, double y, double w, double h, Integer fillArgb,
                       Integer strokeArgb, Double strokeWidth) implements Element {}

    /** 圆/椭圆。 */
    public record Circle(double cx, double cy, double rx, double ry, Integer fillArgb,
                         Integer strokeArgb, Double strokeWidth) implements Element {}

    /** 文本：x,y 为基线起点，fontSize 像素。 */
    public record Text(double x, double y, String content, double fontSize, String fontFamily,
                       Integer fillArgb) implements Element {}

    /** 位图引用：data URI 或相对路径。 */
    public record ImageRef(double x, double y, double w, double h, String href) implements Element {}
}