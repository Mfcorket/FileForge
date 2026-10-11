package com.fileforge.core.spi;

/** 转换过程异常：必须携带确定性原因，供 UI 展示与重试决策。 */
public class ConversionException extends Exception {
    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
