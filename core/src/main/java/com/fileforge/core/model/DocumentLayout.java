package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 文档版面中间模型（§4.2）：pdf/docx/odt 的承载。
 * 以页面序列 + 块内容（段落/表格/图片）抽象版面，供文档类格式互通。
 */
public final class DocumentLayout implements Model {
    private final List<Page> pages;
    private final Map<String, String> metadata;

    public DocumentLayout(List<Page> pages, Map<String, String> metadata) {
        this.pages = pages == null ? List.of() : List.copyOf(pages);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.DOCUMENT_LAYOUT; }

    public List<Page> pages() { return pages; }
    public Map<String, String> metadata() { return metadata; }

    /** 页面：版面宽高（点）+ 块序列。 */
    public record Page(double width, double height, List<Block> blocks) {
        public Page {
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    /** 版面块基类。 */
    public sealed interface Block permits Paragraph, Table {} 

    /** 段落：纯文本 + 可选样式标记。 */
    public record Paragraph(String text) implements Block {}

    /** 表格：表头 + 行。 */
    public record Table(List<String> headers, List<List<String>> rows) implements Block {
        public Table {
            headers = headers == null ? List.of() : List.copyOf(headers);
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }
}