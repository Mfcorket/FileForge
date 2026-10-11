package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 归档条目树中间模型：包含条目列表（路径、大小、时间、是否目录、内容流工厂）。
 */
public final class ArchiveContainer implements Model {
    private final List<ArchiveEntry> entries;
    private final Map<String, String> metadata;

    public ArchiveContainer(List<ArchiveEntry> entries, Map<String, String> metadata) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.ARCHIVE_CONTAINER; }

    public List<ArchiveEntry> entries() { return entries; }
    public Map<String, String> metadata() { return metadata; }

    public static final class ArchiveEntry {
        private final String path;
        private final long size;
        private final long lastModified;
        private final boolean directory;
        private final java.util.function.Supplier<java.io.InputStream> contentSupplier;

        public ArchiveEntry(String path, long size, long lastModified, boolean directory,
                            java.util.function.Supplier<java.io.InputStream> contentSupplier) {
            this.path = path;
            this.size = size;
            this.lastModified = lastModified;
            this.directory = directory;
            this.contentSupplier = contentSupplier;
        }

        public String path() { return path; }
        public long size() { return size; }
        public long lastModified() { return lastModified; }
        public boolean isDirectory() { return directory; }
        public java.io.InputStream openContent() { return contentSupplier == null ? null : contentSupplier.get(); }
    }
}