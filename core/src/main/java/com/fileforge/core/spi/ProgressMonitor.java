package com.fileforge.core.spi;

/** 进度监视器：0..100；实现方负责线程安全与取消检测。 */
public interface ProgressMonitor {
    ProgressMonitor NULL = new ProgressMonitor() {
        @Override public void progress(int percent) {}
        @Override public boolean isCancelled() { return false; }
    };

    /** 报告进度，0~100。 */
    void progress(int percent);

    /** 是否请求取消。解码/编码实现应周期性检查并尽早中止。 */
    boolean isCancelled();

    /** 上报转换警告（BEST_EFFORT 保真度说明、内容丢失提示等）。默认空实现。 */
    default void warning(String message) {}
}