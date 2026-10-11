package com.fileforge.core.codec.text;

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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** CSV 插件（Apache Commons CSV via Jackson，LOSSLESS）。 */
public final class CsvPlugin implements FormatPlugin {

    @Override public String id() { return "csv.codec"; }
    @Override public FormatFamily family() { return FormatFamily.DATA_TABLE; }
    @Override public Set<String> extensions() { return Set.of("csv"); }
    @Override public String mimeType() { return "text/csv"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        // CSV 无魔数，靠扩展名；返回 false 让检测器回退扩展名
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            // 尝试检测编码（简易：假设 UTF-8）
            return TextCodecUtil.csvToTable(in, StandardCharsets.UTF_8, true);
        } catch (IOException e) {
            throw new ConversionException("CSV 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return DataTable.class.isAssignableFrom(modelType) || TextContent.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        try (OutputStream out = sink.openStream()) {
            DataTable table;
            if (model instanceof DataTable dt) {
                table = dt;
            } else if (model instanceof TextContent tc) {
                table = TextCodecUtil.jsonTextToTable(tc);
            } else {
                throw new ConversionException("CSV 编码不支持模型: " + model.getClass());
            }
            TextCodecUtil.tableToCsv(table, out, StandardCharsets.UTF_8, true);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("CSV 写入失败: " + e.getMessage(), e);
        }
    }
}