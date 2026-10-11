package com.fileforge.core.model;

import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.Model;

import java.util.List;
import java.util.Map;

/**
 * 字体信息中间模型：元数据 + 字形覆盖范围等。
 */
public final class FontInfo implements Model {
    private final String familyName;
    private final String fullName;
    private final String postScriptName;
    private final String version;
    private final int unitsPerEm;
    private final int weightClass;
    private final boolean isFixedPitch;
    private final List<Integer> unicodeRanges;
    private final Map<String, String> metadata;

    public FontInfo(String familyName, String fullName, String postScriptName, String version,
                    int unitsPerEm, int weightClass, boolean isFixedPitch,
                    List<Integer> unicodeRanges, Map<String, String> metadata) {
        this.familyName = familyName;
        this.fullName = fullName;
        this.postScriptName = postScriptName;
        this.version = version;
        this.unitsPerEm = unitsPerEm;
        this.weightClass = weightClass;
        this.isFixedPitch = isFixedPitch;
        this.unicodeRanges = unicodeRanges == null ? List.of() : List.copyOf(unicodeRanges);
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; } // 复用文本族

    public String familyName() { return familyName; }
    public String fullName() { return fullName; }
    public String postScriptName() { return postScriptName; }
    public String version() { return version; }
    public int unitsPerEm() { return unitsPerEm; }
    public int weightClass() { return weightClass; }
    public boolean isFixedPitch() { return isFixedPitch; }
    public List<Integer> unicodeRanges() { return unicodeRanges; }
    public Map<String, String> metadata() { return metadata; }
}