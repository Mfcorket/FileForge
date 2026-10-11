package com.fileforge.core.spi;

/**
 * 中间模型标记接口。所有中间模型（RasterImage / VectorDocument / ...）实现本接口。
 * 模型须为不可变或线程安全的值对象，转换引擎可能在虚拟线程间传递。
 */
public interface Model {
    /** 所属格式族。 */
    FormatFamily family();
}
