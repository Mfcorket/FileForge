package com.fileforge.core.engine;

import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.Model;

/**
 * 模型适配器：实现中间模型之间的转换（多跳转换的关键，§4.4）。
 * 例：VideoStream → RasterImage(首帧)、DataTable → TextContent(JSON)、TextContent → DataTable。
 * 无状态、线程安全。
 */
public interface ModelAdapter {
    /** 适配方向：from 模型类型。 */
    Class<? extends Model> fromType();

    /** 适配方向：to 模型类型。 */
    Class<? extends Model> toType();

    /** 执行模型转换。 */
    Model convert(Model source) throws ConversionException;

    /** 本适配器是否能把 src 转换为该 to 类型（可被覆写以做动态判断）。 */
    default boolean canAdapt(Class<? extends Model> from, Class<? extends Model> to) {
        return to == toType() && fromType().isAssignableFrom(from);
    }
}