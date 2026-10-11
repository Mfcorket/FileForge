package com.fileforge.core.codec.archive;

import com.fileforge.core.model.ArchiveContainer;
import com.fileforge.core.model.RasterImage; // 复用 TextContent 存单文件内容
import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** GZIP 插件（单文件压缩/解压，LOSSLESS）。 */
public final class GzipPlugin implements FormatPlugin {

    @Override public String id() { return "gz.codec"; }
    @Override public FormatFamily family() { return FormatFamily.ARCHIVE_CONTAINER; }
    @Override public Set<String> extensions() { return Set.of("gz"); }
    @Override public String mimeType() { return "application/gzip"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 2) return false;
        // GZIP magic: 0x1f 0x8b
        return (header[0] & 0xFF) == 0x1F && (header[1] & 0xFF) == 0x8B;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream();
             var gis = new GzipCompressorInputStream(in)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            IOUtils.copy(gis, buf);
            pm.progress(90);
            // 作为单条目 ArchiveContainer 返回
            byte[] data = buf.toByteArray();
            var entry = new ArchiveContainer.ArchiveEntry(
                    "content", data.length, System.currentTimeMillis(), false,
                    () -> new ByteArrayInputStream(data)
            );
            return new ArchiveContainer(List.of(entry), Map.of("singleEntry", "true"));
        } catch (IOException e) {
            throw new ConversionException("GZIP 解压失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return ArchiveContainer.class.isAssignableFrom(modelType) || TextContent.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        try (OutputStream out = sink.openStream();
             var gos = new GzipCompressorOutputStream(out)) {
            byte[] data;
            if (model instanceof ArchiveContainer arc) {
                if (arc.entries().size() != 1) throw new ConversionException("GZIP 仅支持单文件压缩");
                try (InputStream is = arc.entries().get(0).openContent()) {
                    data = is.readAllBytes();
                }
            } else if (model instanceof TextContent tc) {
                data = tc.text().getBytes(StandardCharsets.UTF_8);
            } else {
                throw new ConversionException("GZIP 编码仅支持 ArchiveContainer(单条目) 或 TextContent");
            }
            gos.write(data);
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("GZIP 压缩失败: " + e.getMessage(), e);
        }
    }
}