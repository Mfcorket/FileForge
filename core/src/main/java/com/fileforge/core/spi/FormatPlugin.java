package com.fileforge.core.spi;

import java.util.Set;

/**
 * 格式插件 SPI（core 定义，见规划文档 §4.3）。
 * <p>
 * 实现要求：无状态、线程安全；经 {@link java.util.ServiceLoader} 或显式注册进入引擎。
 */
public interface FormatPlugin {
    /** 唯一标识，如 "png.decoder"。 */
    String id();

    /** 格式族。 */
    FormatFamily family();

    /** 魔数 / 内容嗅探：给定文件头判断本插件能否解码。 */
    boolean canDecode(byte[] header);

    /** 解码到中间模型。 */
    Model decode(InputSource source, ProgressMonitor pm) throws ConversionException;

    /**
     * 本插件解码产物的真实模型类型（默认按格式族映射，可覆写为更具体类型）。
     * 转换矩阵依据此类型 + 模型适配器推导多跳路径（§4.4）。
     */
    default Class<? extends Model> producedModel() {
        return switch (family()) {
            case RASTER_IMAGE -> com.fileforge.core.model.RasterImage.class;
            case VECTOR_DOCUMENT -> com.fileforge.core.model.VectorDocument.class;
            case TEXT_CONTENT -> com.fileforge.core.model.TextContent.class;
            case DATA_TABLE -> com.fileforge.core.model.DataTable.class;
            case ARCHIVE_CONTAINER -> com.fileforge.core.model.ArchiveContainer.class;
            case AUDIO_STREAM -> com.fileforge.core.model.AudioStream.class;
            case VIDEO_STREAM -> com.fileforge.core.model.VideoStream.class;
            case DOCUMENT_LAYOUT -> com.fileforge.core.model.DocumentLayout.class;
        };
    }

    /** 是否支持把某类中间模型编码为本格式。 */
    boolean supportsEncode(Class<? extends Model> modelType);

    /** 从中间模型编码输出。 */
    void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException;

    /** 扩展名集合（小写、不含点），如 ["png"]。 */
    Set<String> extensions();

    /** MIME 类型。 */
    String mimeType();

    /** 保真度声明。 */
    Capability capability();

    /**
     * 是否需要 java.desktop API（AWT/ImageIO/javax.sound 等）。
     * 返回 true 的插件在 Android 环境下会被自动跳过。
     */
    default boolean requiresDesktop() {
        return false;
    }
}