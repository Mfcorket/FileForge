package com.fileforge.core.codec.archive;

import com.fileforge.core.model.ArchiveContainer;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 7Z 插件（仅解压，Commons Compress，BEST_EFFORT：不支持所有压缩方法）。 */
public final class SevenZPlugin implements FormatPlugin {

    @Override public String id() { return "7z.codec"; }
    @Override public FormatFamily family() { return FormatFamily.ARCHIVE_CONTAINER; }
    @Override public Set<String> extensions() { return Set.of("7z"); }
    @Override public String mimeType() { return "application/x-7z-compressed"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 6) return false;
        // 7z signature: "7z\xBC\xAF\x27\x1C"
        return header[0] == '7' && header[1] == 'z'
                && (header[2] & 0xFF) == 0xBC && (header[3] & 0xFF) == 0xAF
                && (header[4] & 0xFF) == 0x27 && (header[5] & 0xFF) == 0x1C;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        // SevenZFile 需要文件路径或 SeekableByteChannel，这里写临时文件
        Path tmp;
        try {
            tmp = Files.createTempFile("fileforge-", ".7z");
            Files.write(tmp, source.openStream().readAllBytes());
        } catch (IOException e) {
            throw new ConversionException("7Z 临时文件创建失败: " + e.getMessage(), e);
        }
        try (var szf = new SevenZFile(tmp.toFile())) {
            List<ArchiveContainer.ArchiveEntry> entries = new ArrayList<>();
            SevenZArchiveEntry entry;
            int count = 0;
            while ((entry = szf.getNextEntry()) != null) {
                String name = entry.getName();
                long size = entry.getSize();
                long mod = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().toInstant().toEpochMilli() : 0;
                boolean dir = entry.isDirectory();

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                if (!dir) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = szf.read(buffer)) != -1) {
                        buf.write(buffer, 0, n);
                    }
                }
                byte[] data = buf.toByteArray();
                entries.add(new ArchiveContainer.ArchiveEntry(
                        name, size, mod, dir,
                        dir ? null : () -> new ByteArrayInputStream(data)
                ));
                count++;
                if (count % 20 == 0) pm.progress(Math.min(90, count));
            }
            return new ArchiveContainer(entries, Map.of("entryCount", String.valueOf(entries.size())));
        } catch (IOException e) {
            throw new ConversionException("7Z 解压失败（可能不支持该压缩方法）: " + e.getMessage(), e);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (IOException ignored) {}
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return false; // 暂不支持写入
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        throw new ConversionException("7Z 压缩暂不支持");
    }
}