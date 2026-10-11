package com.fileforge.core.codec.svg;

import com.fileforge.core.model.VectorDocument;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SVG 插件（自研，LOSSLESS）：
 * 解码 SVG → VectorDocument；编码 VectorDocument → SVG 文本。
 * 覆盖路径/矩形/圆/文本/图片引用，颜色 #rrggbb / #rgb / 命名色。
 */
public final class SvgPlugin implements FormatPlugin {

    @Override public String id() { return "svg.codec"; }
    @Override public FormatFamily family() { return FormatFamily.VECTOR_DOCUMENT; }
    @Override public Set<String> extensions() { return Set.of("svg"); }
    @Override public String mimeType() { return "image/svg+xml"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null) return false;
        String s = new String(header, StandardCharsets.UTF_8).trim();
        // 跳过 XML 声明/注释后应含 <svg 或带 xmlns 的根元素
        String body = s.replaceFirst("^<\\?xml[^>]*\\?>", "").trim();
        return body.startsWith("<svg") || body.startsWith("<SVG");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setNamespaceAware(true);
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // 防 XXE
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(in);

            Element root = doc.getDocumentElement();
            double width = parseLength(root.getAttribute("width"), 800);
            double height = parseLength(root.getAttribute("height"), 600);

            List<VectorDocument.Element> elements = new ArrayList<>();
            collect(root, elements);
            pm.progress(90);
            return new VectorDocument(width, height, elements, Map.of());
        } catch (IOException e) {
            throw new ConversionException("SVG 读取失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new ConversionException("SVG 解析失败: " + e.getMessage(), e);
        }
    }

    private void collect(Node parent, List<VectorDocument.Element> out) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;
            Element el = (Element) node;
            String tag = el.getTagName().toLowerCase(Locale.ROOT);
            switch (tag) {
                case "path" -> out.add(parsePath(el));
                case "rect" -> out.add(parseRect(el));
                case "circle" -> out.add(parseCircle(el));
                case "ellipse" -> out.add(parseEllipse(el));
                case "text" -> out.add(parseText(el));
                case "image" -> out.add(parseImage(el));
                case "g", "svg", "defs", "a" -> collect(el, out); // 递归子元素
                default -> { /* 忽略不支持元素 */ }
            }
        }
    }

    private static VectorDocument.Element parsePath(Element el) {
        String d = el.getAttribute("d");
        return new VectorDocument.Path(d, colorAttr(el, "fill"), colorAttr(el, "stroke"),
                strokeWidth(el));
    }

    private static VectorDocument.Element parseRect(Element el) {
        double x = parseLength(el.getAttribute("x"), 0);
        double y = parseLength(el.getAttribute("y"), 0);
        double w = parseLength(el.getAttribute("width"), 0);
        double h = parseLength(el.getAttribute("height"), 0);
        return new VectorDocument.Rect(x, y, w, h, colorAttr(el, "fill"), colorAttr(el, "stroke"), strokeWidth(el));
    }

    private static VectorDocument.Element parseCircle(Element el) {
        double cx = parseLength(el.getAttribute("cx"), 0);
        double cy = parseLength(el.getAttribute("cy"), 0);
        double r = parseLength(el.getAttribute("r"), 0);
        return new VectorDocument.Circle(cx, cy, r, r, colorAttr(el, "fill"), colorAttr(el, "stroke"), strokeWidth(el));
    }

    private static VectorDocument.Element parseEllipse(Element el) {
        double cx = parseLength(el.getAttribute("cx"), 0);
        double cy = parseLength(el.getAttribute("cy"), 0);
        double rx = parseLength(el.getAttribute("rx"), 0);
        double ry = parseLength(el.getAttribute("ry"), 0);
        return new VectorDocument.Circle(cx, cy, rx, ry, colorAttr(el, "fill"), colorAttr(el, "stroke"), strokeWidth(el));
    }

    private static VectorDocument.Element parseText(Element el) {
        double x = parseLength(el.getAttribute("x"), 0);
        double y = parseLength(el.getAttribute("y"), 0);
        double fontSize = parseLength(el.getAttribute("font-size"), 12);
        String content = el.getTextContent() == null ? "" : el.getTextContent().trim();
        return new VectorDocument.Text(x, y, content, fontSize, el.getAttribute("font-family"), colorAttr(el, "fill"));
    }

    private static VectorDocument.Element parseImage(Element el) {
        double x = parseLength(el.getAttribute("x"), 0);
        double y = parseLength(el.getAttribute("y"), 0);
        double w = parseLength(el.getAttribute("width"), 0);
        double h = parseLength(el.getAttribute("height"), 0);
        return new VectorDocument.ImageRef(x, y, w, h, el.getAttribute("href").isEmpty()
                ? el.getAttribute("xlink:href") : el.getAttribute("href"));
    }

    // ------------------------------------------------------------ 编码

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return VectorDocument.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof VectorDocument vd)) {
            throw new ConversionException("SVG 编码需要 VectorDocument，收到 " + model.getClass().getSimpleName());
        }
        try (OutputStream out = sink.openStream();
             OutputStreamWriter w = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
            w.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            w.write(String.format(Locale.ROOT, "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.2f\" height=\"%.2f\">\n",
                    vd.width(), vd.height()));
            for (VectorDocument.Element e : vd.elements()) {
                w.write(elementToSvg(e));
            }
            w.write("</svg>\n");
            w.flush();
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("SVG 写入失败: " + e.getMessage(), e);
        }
    }

    private static String elementToSvg(VectorDocument.Element e) {
        if (e instanceof VectorDocument.Path p) {
            return String.format(Locale.ROOT, "  <path d=\"%s\"%s%s%s/>\n",
                    escapeAttr(p.d()), fillAttr(p.fillArgb()), strokeAttr(p.strokeArgb(), p.strokeWidth()), "");
        }
        if (e instanceof VectorDocument.Rect r) {
            return String.format(Locale.ROOT, "  <rect x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\"%s%s/>\n",
                    r.x(), r.y(), r.w(), r.h(), fillAttr(r.fillArgb()), strokeAttr(r.strokeArgb(), r.strokeWidth()));
        }
        if (e instanceof VectorDocument.Circle c) {
            return String.format(Locale.ROOT, "  <ellipse cx=\"%.2f\" cy=\"%.2f\" rx=\"%.2f\" ry=\"%.2f\"%s%s/>\n",
                    c.cx(), c.cy(), c.rx(), c.ry(), fillAttr(c.fillArgb()), strokeAttr(c.strokeArgb(), c.strokeWidth()));
        }
        if (e instanceof VectorDocument.Text t) {
            return String.format(Locale.ROOT, "  <text x=\"%.2f\" y=\"%.2f\" font-size=\"%.2f\"%s%s>%s</text>\n",
                    t.x(), t.y(), t.fontSize(), t.fontFamily() != null && !t.fontFamily().isEmpty()
                            ? " font-family=\"" + escapeAttr(t.fontFamily()) + "\"" : "",
                    fillAttr(t.fillArgb()), escapeText(t.content()));
        }
        if (e instanceof VectorDocument.ImageRef img) {
            return String.format(Locale.ROOT, "  <image x=\"%.2f\" y=\"%.2f\" width=\"%.2f\" height=\"%.2f\" href=\"%s\"/>\n",
                    img.x(), img.y(), img.w(), img.h(), escapeAttr(img.href()));
        }
        return "";
    }

    // ------------------------------------------------------------ 工具

    private static Integer colorAttr(Element el, String name) {
        String v = el.getAttribute(name);
        if (v.isEmpty() || v.equals("none")) return null;
        try { return parseColor(v); } catch (Exception e) { return null; }
    }

    private static Double strokeWidth(Element el) {
        String v = el.getAttribute("stroke-width");
        if (v.isEmpty()) return null;
        try { return parseLength(v, 1); } catch (Exception e) { return null; }
    }

    /** 解析 #rgb/#rrggbb/命名色，返回 ARGB int。 */
    public static int parseColor(String css) throws IllegalArgumentException {
        String v = css.trim();
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            if (hex.length() == 3) {
                int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                return (0xFF << 24) | (r << 16) | (g << 8) | b;
            }
            if (hex.length() == 6) {
                return (0xFF << 24) | Integer.parseInt(hex, 16);
            }
        }
        // 命名色（常用子集）
        return switch (v.toLowerCase(Locale.ROOT)) {
            case "black" -> 0xFF000000;
            case "white" -> 0xFFFFFFFF;
            case "red" -> 0xFFFF0000;
            case "green", "lime" -> 0xFF00FF00;
            case "blue" -> 0xFF0000FF;
            case "yellow" -> 0xFFFFFF00;
            case "cyan", "aqua" -> 0xFF00FFFF;
            case "magenta", "fuchsia" -> 0xFFFF00FF;
            case "gray", "grey" -> 0xFF808080;
            case "orange" -> 0xFFFFA500;
            default -> throw new IllegalArgumentException("未支持的颜色: " + css);
        };
    }

    private static String fillAttr(Integer argb) {
        return argb == null ? "" : " fill=\"" + toHexColor(argb) + "\"";
    }

    private static String strokeAttr(Integer argb, Double width) {
        StringBuilder sb = new StringBuilder();
        if (argb != null) sb.append(" stroke=\"").append(toHexColor(argb)).append("\"");
        if (width != null) sb.append(String.format(Locale.ROOT, " stroke-width=\"%.2f\"", width));
        return sb.toString();
    }

    private static String toHexColor(int argb) {
        return String.format("#%06x", argb & 0xFFFFFF);
    }

    private static double parseLength(String s, double def) {
        if (s == null || s.isEmpty()) return def;
        s = s.trim();
        if (s.endsWith("px")) s = s.substring(0, s.length() - 2);
        else if (s.endsWith("%")) {
            return Double.parseDouble(s.substring(0, s.length() - 1)) / 100.0 * def;
        }
        return Double.parseDouble(s);
    }

    private static String escapeAttr(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;");
    }

    private static String escapeText(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}