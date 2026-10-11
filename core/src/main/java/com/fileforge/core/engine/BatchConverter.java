package com.fileforge.core.engine;

import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 批量转换执行器（§5.2）：虚拟线程池并发执行多个转换，支持线程级取消。
 * <p>
 * 用法：
 * <pre>
 * var batch = new BatchConverter(engine);
 * batch.add(item);
 * batch.start();
 * batch.cancel();
 * </pre>
 */
public final class BatchConverter {
    private final ConversionEngine engine;
    private final List<BatchItem> items = new CopyOnWriteArrayList<>();
    private final AtomicInteger done = new AtomicInteger();
    private final AtomicReference<ExecutorService> pool = new AtomicReference<>();
    private volatile boolean cancelled;
    private volatile boolean paused;
    private final Object pauseGate = new Object();
    private Consumer<BatchResult> onComplete;

    /** 单个转换任务。 */
    public record BatchItem(String label, InputSource source, String fileName,
                            String targetExt, OutputSink sink, ProgressMonitor pm) {
        public BatchItem {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(fileName, "fileName");
            Objects.requireNonNull(targetExt, "targetExt");
            Objects.requireNonNull(sink, "sink");
        }
    }

    /** 单个任务执行结果。 */
    public record BatchResult(String label, boolean success, String error) {
        public static BatchResult ok(String label) { return new BatchResult(label, true, null); }
        public static BatchResult fail(String label, String error) { return new BatchResult(label, false, error); }
    }

    private int threadCount = 4;

    public BatchConverter(ConversionEngine engine) {
        this.engine = engine;
    }

    public void setThreadCount(int count) {
        this.threadCount = Math.max(1, count);
    }

    public void add(BatchItem item) { items.add(item); }

    public int size() { return items.size(); }

    /** 全部完成后回调（FX 线程自行调度）。 */
    public void onComplete(Consumer<BatchResult> callback) { this.onComplete = callback; }

    public void start() {
        if (pool.get() != null) throw new IllegalStateException("batch already started");
        ExecutorService p = java.util.concurrent.Executors.newFixedThreadPool(threadCount, r -> {
            Thread t = Thread.ofVirtual().name("batch-" + threadCount).unstarted(r);
            return t;
        });
        pool.set(p);
        cancelled = false;
        paused = false;
        List<BatchItem> snapshot = List.copyOf(items);
        for (BatchItem item : snapshot) {
            p.submit(() -> {
                awaitResume();
                BatchResult r;
                try {
                    if (cancelled) {
                        r = BatchResult.fail(item.label(), "已取消");
                    } else {
                        engine.convert(item.source(), item.fileName(), item.targetExt(),
                                item.sink(), item.pm());
                        r = BatchResult.ok(item.label());
                    }
                } catch (ConversionException e) {
                    r = BatchResult.fail(item.label(), e.getMessage());
                } catch (Throwable t) {
                    r = BatchResult.fail(item.label(), t.getClass().getSimpleName() + ": " + t.getMessage());
                }
                int n = done.incrementAndGet();
                if (onComplete != null) {
                    onComplete.accept(r);
                }
                if (n == snapshot.size()) {
                    p.shutdown();
                }
            });
        }
    }

    /** 暂停：新任务挂起等待；已运行任务不受影响。 */
    public void pause() {
        paused = true;
    }

    /** 恢复。 */
    public void resume() {
        paused = false;
        synchronized (pauseGate) {
            pauseGate.notifyAll();
        }
    }

    public boolean isPaused() { return paused; }

    private void awaitResume() {
        if (!paused) return;
        synchronized (pauseGate) {
            while (paused) {
                try {
                    pauseGate.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** 请求取消（正在执行的转换经 ProgressMonitor.isCancelled 尽早中止）。 */
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() { return cancelled; }

    public void shutdownNow() {
        ExecutorService p = pool.getAndSet(null);
        if (p != null) p.shutdownNow();
    }
}