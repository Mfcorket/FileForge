package com.fileforge.core.codec.office;

import com.fileforge.core.model.DataTable;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Excel (.xlsx) 插件（Apache POI，解码为 DataTable；每个 sheet 作为一个 DataTable，取第一个 sheet）。
 * 编码暂不支持（仅解码导出）。
 */
public final class ExcelPlugin implements FormatPlugin {

    @Override public String id() { return "xlsx.codec"; }
    @Override public FormatFamily family() { return FormatFamily.DATA_TABLE; }
    @Override public Set<String> extensions() { return Set.of("xlsx"); }
    @Override public String mimeType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        return (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            Workbook wb = new XSSFWorkbook(in);
            if (wb.getNumberOfSheets() == 0) throw new ConversionException("XLSX 无工作表");
            Sheet sheet = wb.getSheetAt(0);
            int lastRow = sheet.getLastRowNum();
            if (lastRow < 0) return new DataTable(List.of(), List.of(), Map.of("sheet", sheet.getSheetName()));

            // 读取表头（第 0 行）
            Row headerRow = sheet.getRow(0);
            List<String> headers = new ArrayList<>();
            if (headerRow != null) {
                for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                    Cell cell = headerRow.getCell(c);
                    headers.add(cell != null ? cell.toString() : "");
                }
            } else {
                // 无表头，按最大列数生成
                int maxCols = 0;
                for (Row r : sheet) maxCols = Math.max(maxCols, r.getLastCellNum());
                for (int c = 0; c < maxCols; c++) headers.add("col" + (c + 1));
            }

            List<List<String>> rows = new ArrayList<>();
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                List<String> rowData = new ArrayList<>(headers.size());
                for (int c = 0; c < headers.size(); c++) {
                    Cell cell = row != null ? row.getCell(c) : null;
                    rowData.add(cell != null ? cell.toString() : "");
                }
                if (rowData.stream().anyMatch(s -> !s.isEmpty())) rows.add(rowData);
                if (r % 50 == 0) pm.progress((int) ((long) r * 80 / lastRow));
            }
            return new DataTable(headers, rows, Map.of("sheet", sheet.getSheetName(), "sheetCount", String.valueOf(wb.getNumberOfSheets())));
        } catch (IOException e) {
            throw new ConversionException("XLSX 解析失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConversionException("XLSX 解析异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return false;
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("XLSX 写入暂不支持（仅解码导出）");
    }
}