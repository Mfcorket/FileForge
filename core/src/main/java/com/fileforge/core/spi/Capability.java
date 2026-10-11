package com.fileforge.core.spi;

/** 转换保真度声明。 */
public enum Capability {
    /** 无损 */
    LOSSLESS,
    /** 有损但可接受 */
    LOSSY,
    /** 尽力兼容，可能丢内容（失败需给出明确警告） */
    BEST_EFFORT
}
