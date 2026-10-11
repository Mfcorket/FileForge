package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 结构化表格中间模型：表头 + 行数据 + 元数据。
 */
public final class DataTable implements Model {
    private final List<String> headers;
    private final List<List<String>> rows;
    private final Map<String, String> metadata;

    public DataTable(List<String> headers, List<List<String>> rows, Map<String, String> metadata) {
        this.headers = headers == null ? List.of() : List.copyOf(headers);
        this.rows = rows == null ? List.of() : List.copyOf(rows);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.DATA_TABLE; }

    public List<String> headers() { return headers; }
    public List<List<String>> rows() { return rows; }
    public Map<String, String> metadata() { return metadata; }
}