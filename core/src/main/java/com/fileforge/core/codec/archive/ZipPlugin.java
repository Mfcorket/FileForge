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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/** ZIP 插件（Commons Compress，LOSSLESS，支持读写）。 */
public final class ZipPlugin implements FormatPlugin {

    @Override public String id() { return "zip.codec"; }
    @Override public FormatFamily family() { return FormatFamily.ARCHIVE_CONTAINER; }
    @Override public Set<String> extensions() { return Set.of("zip"); }
    @Override public String mimeType() { return "application/zip"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // ZIP local file header signature: 0x04034b50 (little-endian: PK\003\004)
        return (header[0] & 0xFF) == 0x50 && (header[1] & 0xFF) == 0x4B
                && (header[2] & 0xFF) == 0x03 && (header[3] & 0xFF) == 0x04;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            var zis = new ZipArchiveInputStream(in);
            List<ArchiveContainer.ArchiveEntry> entries = new ArrayList<>();
            ZipArchiveEntry entry;
            int count = 0;
            while ((entry = zis.getNextZipEntry()) != null) {
                String name = entry.getName();
                long size = entry.getSize();
                long mod = entry.getLastModifiedDate() != null ? entry.getLastModifiedDate().toInstant().toEpochMilli() : 0;
                boolean dir = entry.isDirectory();

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                if (!dir) {
                    IOUtils.copy(zis, buf);
                }
                byte[] data = buf.toByteArray();
                entries.add(new ArchiveContainer.ArchiveEntry(
                        name, size, mod, dir,
                        dir ? null : () -> new ByteArrayInputStream(data)
                ));
                count++;
                if (count % 50 == 0) pm.progress(Math.min(90, count));
            }
            return new ArchiveContainer(entries, Map.of("entryCount", String.valueOf(entries.size())));
        } catch (IOException e) {
            throw new ConversionException("ZIP 解压失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return ArchiveContainer.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof ArchiveContainer arc)) {
            throw new ConversionException("ZIP 编码需要 ArchiveContainer 模型");
        }
        try (OutputStream out = sink.openStream();
             var zos = new ZipArchiveOutputStream(out)) {
            int i = 0;
            for (ArchiveContainer.ArchiveEntry e : arc.entries()) {
                ZipArchiveEntry ze = new ZipArchiveEntry(e.path());
                ze.setSize(e.size());
                if (e.lastModified() > 0) ze.setTime(e.lastModified()); // long millis
                if (e.isDirectory()) ze.setUnixMode(040755);
                if (!e.isDirectory()) {
                    // 计算 CRC32
                    try (InputStream crcIs = e.openContent()) {
                        CRC32 crc = new CRC32();
                        byte[] buffer = new byte[8192];
                        int n;
                        while ((n = crcIs.read(buffer)) != -1) crc.update(buffer, 0, n);
                        ze.setCrc(crc.getValue());
                    }
                }
                zos.putArchiveEntry(ze);
                if (!e.isDirectory()) {
                    try (InputStream contentIs = e.openContent()) {
                        IOUtils.copy(contentIs, zos);
                    }
                }
                zos.closeArchiveEntry();
                i++;
                if (i % 50 == 0) pm.progress(Math.min(90, i));
            }
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("ZIP 压缩失败: " + e.getMessage(), e);
        }
    }
}