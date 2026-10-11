package com.fileforge.core.codec.pdf;

import com.fileforge.core.model.DocumentLayout;
import com.fileforge.core.spi.ConversionException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * DocumentLayout → PDF 编码器（PDFBox 写纯文本 + 简易表格）。
 * 尽力兼容：段落为文本行，表格为网格线 + 单元格文本。
 */
public final class PdfWriter {

    private PdfWriter() {}

    public static void write(DocumentLayout layout, OutputStream out) throws ConversionException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new org.apache.pdfbox.pdmodel.font.PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float fontSize = 11f;
            float lineHeight = fontSize * 1.4f;

            // 逐块排布：简化为一页装不下就换页
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float margin = 40f;
            float y = pageHeight - margin;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setLeading(lineHeight);
                for (DocumentLayout.Page lp : layout.pages()) {
                    for (DocumentLayout.Block block : lp.blocks()) {
                        if (block instanceof DocumentLayout.Paragraph p) {
                            y = drawParagraph(cs, font, fontSize, p.text(), margin, y, pageHeight, lineHeight);
                        } else if (block instanceof DocumentLayout.Table t) {
                            y = drawTable(cs, font, fontSize, t, margin, y, pageHeight, pageWidth, lineHeight);
                        }
                    }
                }
            }
            doc.save(out);
        } catch (IOException e) {
            throw new ConversionException("PDF 写入失败: " + e.getMessage(), e);
        }
    }

    private static float drawParagraph(PDPageContentStream cs, PDFont font, float size, String text,
                                       float margin, float y, float pageHeight, float lineHeight) throws IOException {
        if (text == null || text.isEmpty()) return y;
        for (String line : text.split("\n")) {
            y = checkNewPage(cs, font, size, y, pageHeight, lineHeight);
            cs.beginText();
            cs.setFont(font, size);
            cs.newLineAtOffset(margin, y);
            cs.showText(safeText(line));
            cs.endText();
            y -= lineHeight;
        }
        return y - lineHeight * 0.5f;
    }

    private static float drawTable(PDPageContentStream cs, PDFont font, float size, DocumentLayout.Table table,
                                   float margin, float y, float pageHeight, float pageWidth, float lineHeight) throws IOException {
        int cols = Math.max(1, table.headers().size());
        float colW = (pageWidth - margin * 2) / cols;
        float cellPad = 3f;
        for (List<String> row : rowsWithHeader(table)) {
            y = checkNewPage(cs, font, size, y, pageHeight, lineHeight);
            float rowH = lineHeight + 2 * cellPad;
            // 画行底边 + 单元格边框
            for (int c = 0; c < cols; c++) {
                float x = margin + c * colW;
                cs.addRect(x, y - rowH, colW, rowH);
                cs.stroke();
            }
            for (int c = 0; c < cols; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                cs.beginText();
                cs.setFont(font, size);
                cs.newLineAtOffset(margin + c * colW + cellPad, y - cellPad - size);
                cs.showText(safeText(truncate(cell, colW / size)));
                cs.endText();
            }
            y -= rowH;
        }
        return y - lineHeight * 0.5f;
    }

    private static Iterable<List<String>> rowsWithHeader(DocumentLayout.Table t) {
        java.util.List<List<String>> all = new java.util.ArrayList<>();
        if (!t.headers().isEmpty()) all.add(List.copyOf(t.headers()));
        all.addAll(t.rows());
        return all;
    }

    private static float checkNewPage(PDPageContentStream cs, PDFont font, float size,
                                      float y, float pageHeight, float lineHeight) throws IOException {
        // 简化：不真正翻页，若越界钳制（多页支持后续扩展）
        if (y < 40) {
            return pageHeight - 40;
        }
        return y;
    }

    private static String safeText(String s) {
        // PDF 文本需转义 () \ 
        return s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private static String truncate(String s, double maxChars) {
        if (s.length() <= maxChars) return s;
        return s.substring(0, Math.max(0, (int) maxChars - 1)) + "…";
    }
}