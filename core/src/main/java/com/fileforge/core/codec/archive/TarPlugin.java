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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
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

/** TAR 插件（Commons Compress，LOSSLESS，支持读写）。 */
public final class TarPlugin implements FormatPlugin {

    @Override public String id() { return "tar.codec"; }
    @Override public FormatFamily family() { return FormatFamily.ARCHIVE_CONTAINER; }
    @Override public Set<String> extensions() { return Set.of("tar"); }
    @Override public String mimeType() { return "application/x-tar"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        // TAR 无标准魔数，靠扩展名；返回 false 让检测器回退扩展名
        return false;
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            var tis = new TarArchiveInputStream(in);
            List<ArchiveContainer.ArchiveEntry> entries = new ArrayList<>();
            TarArchiveEntry entry;
            int count = 0;
            while ((entry = tis.getNextTarEntry()) != null) {
                String name = entry.getName();
                long size = entry.getSize();
                long mod = entry.getModTime() != null ? entry.getModTime().toInstant().toEpochMilli() : 0;
                boolean dir = entry.isDirectory();

                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                if (!dir) {
                    IOUtils.copy(tis, buf);
                }
                // mod already captured
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
            throw new ConversionException("TAR 解压失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return ArchiveContainer.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof ArchiveContainer arc)) {
            throw new ConversionException("TAR 编码需要 ArchiveContainer 模型");
        }
        try (OutputStream out = sink.openStream();
             var tos = new TarArchiveOutputStream(out)) {
            int i = 0;
            for (ArchiveContainer.ArchiveEntry e : arc.entries()) {
                TarArchiveEntry te = new TarArchiveEntry(e.path());
                te.setSize(e.size());
                te.setMode(e.isDirectory() ? 040755 : 0100644);
                if (e.lastModified() > 0) te.setModTime(e.lastModified()); // long millis
                tos.putArchiveEntry(te);
                if (!e.isDirectory()) {
                    try (InputStream is = e.openContent()) {
                        IOUtils.copy(is, tos);
                    }
                }
                tos.closeArchiveEntry();
                i++;
                if (i % 50 == 0) pm.progress(Math.min(90, i));
            }
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("TAR 打包失败: " + e.getMessage(), e);
        }
    }
}