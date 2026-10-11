package com.fileforge.desktop;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.nio.file.Path;

/** 批量队列中的单个转换任务项（JavaFX 可观察模型）。 */
public final class ConversionItem {
    enum Status { PENDING, RUNNING, DONE, FAILED, CANCELLED }

    private final Path file;
    private final StringProperty status = new SimpleStringProperty(Status.PENDING.name());
    private final StringProperty message = new SimpleStringProperty("");
    private final DoubleProperty progress = new SimpleDoubleProperty(0);
    volatile boolean cancelRequested;

    public ConversionItem(Path file) {
        this.file = file;
    }

    public Path file() { return file; }

    public StringProperty statusProperty() { return status; }
    public StringProperty messageProperty() { return message; }
    public DoubleProperty progressProperty() { return progress; }

    public Status status() {
        try { return Status.valueOf(status.get()); } catch (Exception e) { return Status.PENDING; }
    }

    public void setStatus(Status s) { status.set(s.name()); }
    public void setMessage(String m) { message.set(m); }
    public void setProgress(double p) { progress.set(p); }

    public void requestCancel() { cancelRequested = true; }
    public boolean isCancelRequested() { return cancelRequested; }

    /** 失败项重试：重置状态。 */
    public void reset() {
        cancelRequested = false;
        setStatus(Status.PENDING);
        setMessage("");
        setProgress(0);
    }
}
