package com.fileforge.core.engine;

import com.fileforge.core.model.DataTable;
import com.fileforge.core.model.DocumentLayout;
import com.fileforge.core.model.RasterImage;
import com.fileforge.core.model.TextContent;
import com.fileforge.core.model.VideoStream;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.Model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模型适配器注册表：管理所有 ModelAdapter 并按需查找。
 * 内置常用适配：
 * <ul>
 *   <li>VideoStream → RasterImage（抽首帧，供图片导出）</li>
 *   <li>DataTable → TextContent（JSON 数组文本）</li>
 *   <li>TextContent(JSON数组) → DataTable</li>
 *   <li>TextContent → RasterImage（纯文本转图片，简易）</li>
 * </ul>
 * 线程安全。
 */
public final class ModelAdapters {
    private final List<ModelAdapter> adapters = new CopyOnWriteArrayList<>();

    public ModelAdapters() {
        registerBuiltins();
    }

    public void register(ModelAdapter adapter) {
        adapters.add(adapter);
    }

    public List<ModelAdapter> adapters() { return List.copyOf(adapters); }

    private void registerBuiltins() {
        register(new VideoToRaster());
        register(new DataTableToText());
        register(new TextToDataTable());
        register(new RasterToVector());
        register(new TextToDocumentLayout());
    }

    // ------------------------------------------------------------ 内置适配器

    /** VideoStream → RasterImage（取首帧）。 */
    public static final class VideoToRaster implements ModelAdapter {
        @Override public Class<? extends Model> fromType() { return VideoStream.class; }
        @Override public Class<? extends Model> toType() { return RasterImage.class; }

        @Override
        public Model convert(Model source) throws ConversionException {
            VideoStream vs = (VideoStream) source;
            if (vs.frames().isEmpty()) throw new ConversionException("视频无帧可适配");
            return vs.frames().get(0);
        }
    }

    /** DataTable → TextContent（JSON 数组）。 */
    public static final class DataTableToText implements ModelAdapter {
        @Override public Class<? extends Model> fromType() { return DataTable.class; }
        @Override public Class<? extends Model> toType() { return TextContent.class; }

        @Override
        public Model convert(Model source) throws ConversionException {
            DataTable dt = (DataTable) source;
            List<Map<String, String>> list = new ArrayList<>();
            for (List<String> row : dt.rows()) {
                Map<String, String> m = new java.util.LinkedHashMap<>();
                for (int i = 0; i < dt.headers().size(); i++) {
                    m.put(dt.headers().get(i), i < row.size() ? row.get(i) : "");
                }
                list.add(m);
            }
            String json;
            try {
                json = com.fileforge.core.codec.text.TextCodecUtil.toJson(list);
            } catch (ConversionException e) {
                throw e;
            }
            return new TextContent(json, java.nio.charset.StandardCharsets.UTF_8, Map.of());
        }
    }

    /** TextContent(JSON 数组) → DataTable。 */
    public static final class TextToDataTable implements ModelAdapter {
        @Override public Class<? extends Model> fromType() { return TextContent.class; }
        @Override public Class<? extends Model> toType() { return DataTable.class; }

        @Override
        public Model convert(Model source) throws ConversionException {
            TextContent tc = (TextContent) source;
            return com.fileforge.core.codec.text.TextCodecUtil.jsonTextToTable(tc);
        }
    }

    /** RasterImage → VectorDocument：像素内嵌为 base64 PNG data URI（SVG 图片引用）。 */
    public static final class RasterToVector implements ModelAdapter {
        @Override public Class<? extends Model> fromType() { return RasterImage.class; }
        @Override public Class<? extends Model> toType() { return com.fileforge.core.model.VectorDocument.class; }

        @Override
        public Model convert(Model source) throws ConversionException {
            RasterImage img = (RasterImage) source;
            // 用 PNG 编码器把像素转为字节，再 base64
            try {
                var png = new com.fileforge.core.codec.png.PngPlugin();
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                png.encode(img, () -> buf, com.fileforge.core.spi.ProgressMonitor.NULL);
                String b64 = java.util.Base64.getEncoder().encodeToString(buf.toByteArray());
                String href = "data:image/png;base64," + b64;
                var imgRef = new com.fileforge.core.model.VectorDocument.ImageRef(
                        0, 0, img.width(), img.height(), href);
                return new com.fileforge.core.model.VectorDocument(
                        img.width(), img.height(), List.of(imgRef), Map.of());
            } catch (ConversionException e) {
                throw e;
            } catch (Exception e) {
                throw new ConversionException("RasterImage→Vector 适配失败: " + e.getMessage(), e);
            }
        }
    }

    /** TextContent → DocumentLayout：按行拆段，识别 [TABLE] 标记的制表符表格。 */
    public static final class TextToDocumentLayout implements ModelAdapter {
        @Override public Class<? extends Model> fromType() { return TextContent.class; }
        @Override public Class<? extends Model> toType() { return DocumentLayout.class; }

        @Override
        public Model convert(Model source) throws ConversionException {
            TextContent tc = (TextContent) source;
            String text = tc.text();
            if (text == null || text.isBlank()) {
                return new DocumentLayout(List.of(
                        new DocumentLayout.Page(595, 842, List.of())), Map.of());
            }
            List<DocumentLayout.Block> blocks = new ArrayList<>();
            boolean inTable = false;
            List<String> tableRows = new ArrayList<>();
            for (String line : text.split("\n")) {
                String t = line.trim();
                if (t.equals("[TABLE]")) {
                    inTable = true;
                    tableRows = new ArrayList<>();
                    continue;
                }
                if (inTable) {
                    if (t.isEmpty()) { // 表格结束
                        flushTable(blocks, tableRows);
                        inTable = false;
                    } else {
                        tableRows.add(t);
                    }
                } else {
                    if (!t.isEmpty()) blocks.add(new DocumentLayout.Paragraph(t));
                }
            }
            if (inTable) flushTable(blocks, tableRows);
            if (blocks.isEmpty()) blocks.add(new DocumentLayout.Paragraph(""));

            return new DocumentLayout(List.of(
                    new DocumentLayout.Page(595, 842, List.copyOf(blocks))), Map.of());
        }

        private static void flushTable(List<DocumentLayout.Block> blocks, List<String> rows) {
            if (rows.isEmpty()) return;
            List<String> headers = new ArrayList<>();
            List<List<String>> data = new ArrayList<>();
            boolean first = true;
            for (String row : rows) {
                List<String> cells = new ArrayList<>();
                for (String cell : row.split("\t")) cells.add(cell.trim());
                if (first) {
                    headers.addAll(cells);
                    first = false;
                } else {
                    data.add(cells);
                }
            }
            blocks.add(new DocumentLayout.Table(headers, data));
        }
    }
}