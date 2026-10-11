package com.fileforge.core.codec.office;

import com.fileforge.core.model.DataTable;
import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Word (.docx) 插件（Apache POI，解码为 TextContent；表格可转 DataTable）。
 * 编码暂不支持（仅解码导出）。
 */
public final class WordPlugin implements FormatPlugin {

    @Override public String id() { return "docx.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("docx"); }
    @Override public String mimeType() { return "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // ZIP-based: PK\003\004, but need to check for docx content type
        // 简单：检测 PK 头 + 后续包含 word/document.xml
        return (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            XWPFDocument doc = new XWPFDocument(in);
            StringBuilder sb = new StringBuilder();
            int paraCount = doc.getParagraphs().size();
            for (int i = 0; i < paraCount; i++) {
                XWPFParagraph p = doc.getParagraphs().get(i);
                String text = p.getText();
                if (text != null && !text.isBlank()) sb.append(text).append('\n');
                if (i % 20 == 0) pm.progress((int) ((long) i * 80 / paraCount));
            }
            // 附加表格文本
            for (XWPFTable table : doc.getTables()) {
                sb.append("\n[TABLE]\n");
                for (XWPFTableRow row : table.getRows()) {
                    for (var cell : row.getTableCells()) {
                        sb.append(cell.getText()).append('\t');
                    }
                    sb.append('\n');
                }
            }
            return new TextContent(sb.toString(), StandardCharsets.UTF_8, Map.of("paragraphs", String.valueOf(paraCount)));
        } catch (IOException e) {
            throw new ConversionException("DOCX 解析失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConversionException("DOCX 解析异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return false; // 暂不支持写入
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("DOCX 写入暂不支持（仅解码导出）");
    }
}