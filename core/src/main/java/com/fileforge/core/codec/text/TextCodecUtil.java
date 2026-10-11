package com.fileforge.core.codec.text;

import com.fileforge.core.model.DataTable;
import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.ConversionException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文本/数据格式共用工具：Jackson ObjectMapper 单例 + 转换辅助。
 */
public final class TextCodecUtil {

    private TextCodecUtil() {}

    // --- JSON ---
    public static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    // --- YAML ---
    public static final YAMLMapper YAML_MAPPER = new YAMLMapper();

    // --- CSV ---
    public static final CsvMapper CSV_MAPPER = new CsvMapper();

    // --- XML (使用 Jackson XML) ---
    public static final ObjectMapper XML_MAPPER = new com.fasterxml.jackson.dataformat.xml.XmlMapper();

    /** 将任意对象序列化为 JSON 字符串（pretty）。 */
    public static String toJson(Object obj) throws ConversionException {
        try { return JSON_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (IOException e) { throw new ConversionException("JSON 序列化失败: " + e.getMessage(), e); }
    }

    /** 解析 JSON 为 JsonNode。 */
    public static JsonNode parseJson(String text) throws ConversionException {
        try { return JSON_MAPPER.readTree(text); }
        catch (IOException e) { throw new ConversionException("JSON 解析失败: " + e.getMessage(), e); }
    }

    /** 将 JsonNode 转为 TextContent。 */
    public static TextContent jsonToText(JsonNode node, Charset charset) {
        return new TextContent(node.toPrettyString(), charset, Map.of());
    }

    /** TextContent -> JsonNode。 */
    public static JsonNode textToJson(TextContent tc) throws ConversionException {
        return parseJson(tc.text());
    }

    /** YAML <-> TextContent。 */
    public static String toYaml(Object obj) throws ConversionException {
        try { return YAML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (IOException e) { throw new ConversionException("YAML 序列化失败: " + e.getMessage(), e); }
    }
    public static TextContent yamlToText(String yaml, Charset charset) {
        return new TextContent(yaml, charset, Map.of());
    }
    public static Object parseYaml(String yaml) throws ConversionException {
        try { return YAML_MAPPER.readValue(yaml, Object.class); }
        catch (IOException e) { throw new ConversionException("YAML 解析失败: " + e.getMessage(), e); }
    }

    /** XML <-> TextContent。 */
    public static String toXml(Object obj) throws ConversionException {
        try { return XML_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(obj); }
        catch (IOException e) { throw new ConversionException("XML 序列化失败: " + e.getMessage(), e); }
    }
    public static TextContent xmlToText(String xml, Charset charset) {
        return new TextContent(xml, charset, Map.of());
    }
    public static Object parseXml(String xml) throws ConversionException {
        try { return XML_MAPPER.readValue(xml, Object.class); }
        catch (IOException e) { throw new ConversionException("XML 解析失败: " + e.getMessage(), e); }
    }

    /** CSV <-> DataTable。 */
    public static DataTable csvToTable(InputStream in, Charset charset, boolean hasHeader) throws ConversionException {
        try {
            CsvSchema schema;
            Class<?> contentType;
            if (hasHeader) {
                schema = CSV_MAPPER.schemaFor(Map.class).withHeader();
                contentType = Map.class;
            } else {
                schema = CSV_MAPPER.schemaFor(List.class).withoutHeader();
                contentType = List.class;
            }
            var reader = CSV_MAPPER.readerFor(contentType).with(schema);
            var it = reader.readValues(new java.io.InputStreamReader(in, charset));
            List<List<String>> all = new ArrayList<>();
            List<String> headers = new ArrayList<>();
            boolean first = true;
            while (it.hasNext()) {
                Object rowObj = it.next();
                List<String> row;
                if (rowObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) rowObj;
                    if (first) {
                        headers.addAll(map.keySet());
                        first = false;
                    }
                    row = new ArrayList<>();
                    for (String h : headers) row.add(map.getOrDefault(h, ""));
                } else {
                    @SuppressWarnings("unchecked")
                    List<String> listRow = (List<String>) rowObj;
                    row = listRow;
                    if (first && hasHeader) {
                        headers.addAll(row);
                        first = false;
                        continue;
                    }
                }
                all.add(row);
            }
            if (headers.isEmpty() && !all.isEmpty()) headers = generateHeaders(all.get(0).size());
            return new DataTable(headers, all, Map.of());
        } catch (IOException e) {
            throw new ConversionException("CSV 解析失败: " + e.getMessage(), e);
        }
    }

    public static void tableToCsv(DataTable table, OutputStream out, Charset charset, boolean writeHeader) throws ConversionException {
        try {
            CsvSchema schema;
            if (writeHeader) {
                CsvSchema.Builder sb = CsvSchema.builder().setUseHeader(true);
                for (String h : table.headers()) sb.addColumn(h);
                schema = sb.build();
            } else {
                schema = CsvSchema.emptySchema().withoutHeader();
            }
            var writer = CSV_MAPPER.writer(schema);
            try (var osw = new OutputStreamWriter(out, charset)) {
                // 转为 Map 列表以便带表头写入
                List<Map<String, String>> maps = new ArrayList<>();
                for (List<String> row : table.rows()) {
                    Map<String, String> m = new LinkedHashMap<>();
                    for (int i = 0; i < table.headers().size(); i++) {
                        m.put(table.headers().get(i), i < row.size() ? row.get(i) : "");
                    }
                    maps.add(m);
                }
                writer.writeValues(osw).writeAll(maps);
            }
        } catch (IOException e) {
            throw new ConversionException("CSV 写入失败: " + e.getMessage(), e);
        }
    }

    /** DataTable -> TextContent (JSON 数组)，用于跨格式转换。 */
    public static TextContent tableToJsonText(DataTable table) throws ConversionException {
        List<Map<String, String>> list = new ArrayList<>();
        for (List<String> row : table.rows()) {
            Map<String, String> m = new LinkedHashMap<>();
            for (int i = 0; i < table.headers().size(); i++) {
                m.put(table.headers().get(i), i < row.size() ? row.get(i) : "");
            }
            list.add(m);
        }
        return new TextContent(toJson(list), StandardCharsets.UTF_8, Map.of());
    }

    /** TextContent(JSON 数组) -> DataTable。 */
    public static DataTable jsonTextToTable(TextContent tc) throws ConversionException {
        JsonNode arr = textToJson(tc);
        if (!arr.isArray()) throw new ConversionException("JSON 非数组，无法转 DataTable");
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();
        for (JsonNode obj : arr) {
            if (obj.isObject()) {
                if (headers.isEmpty()) {
                    obj.fieldNames().forEachRemaining(headers::add);
                }
                List<String> row = new ArrayList<>();
                for (String h : headers) row.add(obj.has(h) ? obj.get(h).asText() : "");
                rows.add(row);
            }
        }
        return new DataTable(headers, rows, Map.of());
    }

    private static List<String> generateHeaders(int n) {
        List<String> h = new ArrayList<>(n);
        for (int i = 0; i < n; i++) h.add("col" + (i + 1));
        return h;
    }
}