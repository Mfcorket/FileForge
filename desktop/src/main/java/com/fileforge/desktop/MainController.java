package com.fileforge.desktop;

import com.fileforge.core.codec.video.VideoDecoder;
import com.fileforge.core.engine.BatchConverter;
import com.fileforge.core.engine.ConversionEngine;
import com.fileforge.core.model.AudioStream;
import com.fileforge.core.model.DocumentLayout;
import com.fileforge.core.model.RasterImage;
import com.fileforge.core.model.TextContent;
import com.fileforge.core.model.VectorDocument;
import com.fileforge.core.model.VideoStream;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 主界面控制器：拖拽区 / 文件列表 / 目标格式（按插件矩阵动态生成）/ 输出目录 / 批量队列。
 * 转换经 BatchConverter（虚拟线程）执行，进度/状态经 Platform.runLater 回投 FX 线程。
 */
public final class MainController {

    private final ConversionEngine engine;
    private final BorderPane root = new BorderPane();
    private final ObservableList<ConversionItem> items = FXCollections.observableArrayList();
    private final ListView<ConversionItem> listView = new ListView<>(items);
    private final ComboBox<String> targetFormat = new ComboBox<>();
    private final TextField outputDir = new TextField();
    private final Button startBtn = new Button("开始转换");
    private final Button cancelBtn = new Button("全部取消");
    private final Button pauseBtn = new Button("暂停");
    private final Label summary = new Label("拖入文件开始");
    private final ImageView previewImage = new ImageView();
    private final TextArea previewText = new TextArea();
    private final StackPane previewPane = new StackPane();
    private final Label previewTitle = new Label("预览");

    private volatile BatchConverter currentBatch;

    // 设置字段
    private int threadCount = 4;
    private SettingsDialog.OverwriteStrategy overwriteStrategy = SettingsDialog.OverwriteStrategy.RENAME;
    private String namingPattern = "${name}.${ext}";
    private int jpegQuality = 90;
    private boolean preserveMetadata = true;
    private boolean verifyOutput = false;

    public MainController(ConversionEngine engine) {
        this.engine = engine;
        buildUi();
    }

    public BorderPane root() { return root; }

    private void buildUi() {
        // 顶部：目标格式 + 输出目录
        Label targetLabel = new Label("目标格式:");
        targetFormat.setPrefWidth(140);
        targetFormat.setDisable(true);

        outputDir.setPromptText("输出目录（默认同源目录）");
        outputDir.setEditable(true);
        HBox.setHgrow(outputDir, Priority.ALWAYS);
        Button browseBtn = new Button("浏览…");
        browseBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            var win = root.getScene() == null ? null : root.getScene().getWindow();
            var dir = dc.showDialog(win);
            if (dir != null) outputDir.setText(dir.getAbsolutePath());
        });

        HBox top = new HBox(8, targetLabel, targetFormat, new Label("  输出目录:"), outputDir, browseBtn);
        top.setAlignment(Pos.CENTER_LEFT);
        top.setPadding(new Insets(10));

        // 设置按钮
        Button settingsBtn = new Button("设置");
        settingsBtn.setOnAction(e -> {
            SettingsDialog.Result r = SettingsDialog.showAndWait(root.getScene().getWindow());
            if (r != null) applySettings(r);
        });
        HBox topBar = new HBox(8, top, new Label("|"), settingsBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(10));

        // 中部：文件列表（支持拖拽 + 双击移除已完成项）
        listView.setCellFactory(lv -> new ItemCell());
        listView.setPlaceholder(new Label("将文件拖拽到此处，或双击列表移除已完成项"));
        listView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                ConversionItem sel = listView.getSelectionModel().getSelectedItem();
                if (sel != null && (sel.status() == ConversionItem.Status.DONE
                        || sel.status() == ConversionItem.Status.FAILED
                        || sel.status() == ConversionItem.Status.CANCELLED)) {
                    items.remove(sel);
                    refreshTargets();
                }
            }
        });
        listView.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        listView.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            if (db.hasFiles()) {
                db.getFiles().forEach(f -> addFile(f.toPath()));
                e.setDropCompleted(true);
            }
            e.consume();
        });
        // 选中文件 → 预览
        listView.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            if (newV != null) showPreview(newV);
        });

        // 底部：操作按钮 + 汇总
        startBtn.setDisable(true);
        startBtn.setOnAction(e -> startBatch());
        cancelBtn.setDisable(true);
        cancelBtn.setOnAction(e -> {
            items.forEach(ConversionItem::requestCancel);
            BatchConverter b = currentBatch;
            if (b != null) b.cancel();
        });
        pauseBtn.setDisable(true);
        pauseBtn.setOnAction(e -> {
            BatchConverter b = currentBatch;
            if (b == null) return;
            if (b.isPaused()) {
                b.resume();
                pauseBtn.setText("暂停");
            } else {
                b.pause();
                pauseBtn.setText("继续");
            }
        });
        Button retryBtn = new Button("重试失败项");
        retryBtn.setOnAction(e -> {
            items.stream().filter(i -> i.status() == ConversionItem.Status.FAILED
                    || i.status() == ConversionItem.Status.CANCELLED).forEach(ConversionItem::reset);
            startBatch();
        });
        Button clearBtn = new Button("清空列表");
        clearBtn.setOnAction(e -> { items.clear(); refreshTargets(); });

        HBox bottom = new HBox(8, startBtn, cancelBtn, pauseBtn, retryBtn, clearBtn, summary);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10));
        HBox.setHgrow(summary, Priority.ALWAYS);

        // 预览面板
        previewImage.setPreserveRatio(true);
        previewImage.setFitWidth(280);
        previewText.setEditable(false);
        previewText.setWrapText(true);
        previewText.setPromptText("选择文件查看内容预览");
        previewPane.getChildren().add(previewImage);
        VBox previewBox = new VBox(6, previewTitle, previewPane, previewText);
        previewBox.setPadding(new Insets(10));
        VBox.setVgrow(previewPane, Priority.ALWAYS);
        SplitPane split = new SplitPane(listView, previewBox);
        split.setDividerPositions(0.72);

        root.setTop(top);
        root.setCenter(split);
        root.setBottom(bottom);
    }

    // ------------------------------------------------------------ 预览

    /** 预览选中文件：尽力解码为图片、文本、音频或视频展示（后台线程解码，避免卡 UI）。 */
    private void showPreview(ConversionItem item) {
        previewTitle.setText(item.file().getFileName().toString());
        previewImage.setImage(null);
        previewText.clear();
        Thread.ofVirtual().name("preview-" + item.file().getFileName()).start(() -> {
            try {
                var srcOpt = engine.detector().detect(
                        InputSource.of(item.file()), item.file().getFileName().toString());
                if (srcOpt.isEmpty()) {
                    Platform.runLater(() -> previewText.setText("无法识别的格式"));
                    return;
                }
                FormatPlugin src = srcOpt.get();
                var model = src.decode(InputSource.of(item.file()), ProgressMonitor.NULL);
                if (model instanceof RasterImage ri) {
                    byte[] pngBytes;
                    try {
                        var png = new com.fileforge.core.codec.png.PngPlugin();
                        var out = new ByteArrayOutputStream();
                        png.encode(ri, () -> out, ProgressMonitor.NULL);
                        pngBytes = out.toByteArray();
                    } catch (ConversionException e) {
                        Platform.runLater(() -> previewText.setText("图片预览失败: " + e.getMessage()));
                        return;
                    }
                    Image img = new Image(new ByteArrayInputStream(pngBytes));
                    Platform.runLater(() -> {
                        previewImage.setImage(img);
                        previewText.setText(String.format("图片 %dx%d", ri.width(), ri.height()));
                    });
                } else if (model instanceof TextContent tc) {
                    String text = tc.text();
                    String shown = text.length() > 2000 ? text.substring(0, 2000) + "\n…(截断)" : text;
                    Platform.runLater(() -> previewText.setText(shown));
                } else if (model instanceof AudioStream as) {
                    // 音频：显示基本信息 + 播放控件
                    Platform.runLater(() -> {
                        previewText.setText(String.format(
                                "音频: %.2f秒, %d声道, %dHz",
                                (double) as.frameCount() / as.sampleRate(), as.channels(), as.sampleRate()));
                        previewImage.setImage(null);
                    });
                    // 用文件 URI 直接加载音频（JavaFX Media 支持文件协议）
                    Platform.runLater(() -> {
                        try {
                            Media media = new Media(item.file().toUri().toString());
                            MediaPlayer player = new MediaPlayer(media);
                            MediaView view = new MediaView(player);
                            view.setFitWidth(280);
                            view.setPreserveRatio(true);
                            Button playBtn = new Button("▶ 播放");
                            playBtn.setOnAction(e -> player.play());
                            Button pauseBtn = new Button("❚❚ 暂停");
                            pauseBtn.setOnAction(e -> player.pause());
                            HBox controls = new HBox(8, playBtn, pauseBtn);
                            VBox audioBox = new VBox(6, previewText, controls);
                            previewPane.getChildren().setAll(audioBox);
                        } catch (RuntimeException e) {
                            previewText.setText(previewText.getText() + "\n[JavaFX 无法播放该音频格式]");
                        }
                    });
                } else if (model instanceof VideoStream vs) {
                    // 视频：提取首帧显示
                    previewImage.setImage(null);
                    Platform.runLater(() -> previewText.setText("正在提取视频首帧..."));
                    try {
                        RasterImage firstFrame = VideoDecoder.decodeFirstFrame(item.file());
                        if (firstFrame != null) {
                            byte[] pngBytes;
                            try {
                                var png = new com.fileforge.core.codec.png.PngPlugin();
                                var out = new ByteArrayOutputStream();
                                png.encode(firstFrame, () -> out, ProgressMonitor.NULL);
                                pngBytes = out.toByteArray();
                                Image img = new Image(new ByteArrayInputStream(pngBytes));
                                Platform.runLater(() -> {
                                    previewImage.setImage(img);
                                    previewText.setText(String.format("视频首帧 %dx%d", firstFrame.width(), firstFrame.height()));
                                });
                            } catch (ConversionException e) {
                                Platform.runLater(() -> previewText.setText("首帧编码失败: " + e.getMessage()));
                            }
                        } else {
                            Platform.runLater(() -> previewText.setText("视频无帧或无法解码"));
                        }
                    } catch (ConversionException e) {
                        Platform.runLater(() -> previewText.setText("视频首帧提取失败: " + e.getMessage()));
                    }
                } else if (model instanceof VectorDocument vd) {
                    // 矢量文档：暂不支持渲染
                    Platform.runLater(() -> previewText.setText("矢量文档预览 (暂不支持渲染)"));
                } else if (model instanceof DocumentLayout dl) {
                    // 文档版面：提取文本预览
                    StringBuilder sb = new StringBuilder();
                    for (DocumentLayout.Page page : dl.pages()) {
                        sb.append("=== 第 ").append(dl.pages().indexOf(page) + 1).append(" 页 ===\n");
                        for (DocumentLayout.Block block : page.blocks()) {
                            if (block instanceof DocumentLayout.Paragraph p) {
                                sb.append(p.text()).append("\n");
                            } else if (block instanceof DocumentLayout.Table t) {
                                sb.append("[表格] ").append(t.headers()).append("\n");
                                for (List<String> row : t.rows()) sb.append(String.join("\t", row)).append("\n");
                            }
                        }
                        sb.append("\n");
                    }
                    Platform.runLater(() -> previewText.setText(sb.toString()));
                } else {
                    Platform.runLater(() -> previewText.setText(
                            "预览支持: 图片 / 文本 / 音频 / 视频 / 文档。当前格式: " + src.id()));
                }
            } catch (ConversionException | IOException e) {
                Platform.runLater(() -> previewText.setText("预览失败: " + e.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------ 文件加入

    private void addFile(Path path) {
        if (items.stream().anyMatch(i -> i.file().equals(path))) return;
        items.add(new ConversionItem(path));
        refreshTargets();
    }

    /** 依据所有待处理文件的可解码插件，计算目标格式交集（§5.3 动态生成）。 */
    private void refreshTargets() {
        Set<String> common = null;
        for (ConversionItem item : items) {
            if (item.status() == ConversionItem.Status.DONE) continue;
            Set<String> exts = new LinkedHashSet<>();
            try {
                Optional<FormatPlugin> src = engine.detector()
                        .detect(InputSource.of(item.file()), item.file().getFileName().toString());
                if (src.isPresent()) {
                    String srcExt = src.get().extensions().stream().findFirst().orElse("");
                    for (FormatPlugin dst : engine.matrix().targetsFor(src.get(), engine.registry())) {
                        for (String ext : dst.extensions()) {
                            if (!ext.equalsIgnoreCase(srcExt)) exts.add(ext); // 排除转为自身
                        }
                    }
                }
            } catch (IOException e) {
                item.setStatus(ConversionItem.Status.FAILED);
                item.setMessage("读取失败: " + e.getMessage());
            }
            common = (common == null) ? exts : intersect(common, exts);
        }
        var list = FXCollections.<String>observableArrayList();
        if (common != null) list.addAll(common.stream().sorted().toList());
        targetFormat.setItems(list);
        targetFormat.setDisable(list.isEmpty());
        startBtn.setDisable(list.isEmpty());
        if (!list.isEmpty()) targetFormat.getSelectionModel().selectFirst();
        summary.setText(items.size() + " 个文件，" + list.size() + " 种可行目标格式");
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> r = new LinkedHashSet<>(a);
        r.retainAll(b);
        return r;
    }

    // ------------------------------------------------------------ 批量执行

    private void startBatch() {
        String target = targetFormat.getSelectionModel().getSelectedItem();
        if (target == null) return;
        Path outDir = outputDir.getText().isBlank() ? null : Path.of(outputDir.getText());

        startBtn.setDisable(true);
        cancelBtn.setDisable(false);
        pauseBtn.setDisable(false);
        pauseBtn.setText("暂停");

        // 构建批量任务（跳过已完成项）
        BatchConverter batch = new BatchConverter(engine);
        List<ConversionItem> snapshot = List.copyOf(items);
        int total = (int) snapshot.stream()
                .filter(i -> i.status() != ConversionItem.Status.DONE && !i.isCancelRequested()).count();
        java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();

        for (ConversionItem item : snapshot) {
            if (item.status() == ConversionItem.Status.DONE || item.isCancelRequested()) continue;
            String fileName = item.file().getFileName().toString();
            String baseName = fileName.contains(".")
                    ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            Path dir = outDir != null ? outDir : item.file().getParent();
            Path outPath = dir.resolve(baseName + "." + target);

            ProgressMonitor pm = new ProgressMonitor() {
                @Override public void progress(int percent) {
                    Platform.runLater(() -> item.setProgress(percent / 100.0));
                }
                @Override public boolean isCancelled() { return item.isCancelRequested() || batch.isCancelled(); }
                @Override public void warning(String message) {
                    Platform.runLater(() -> item.setMessage("警告: " + message));
                }
            };

            batch.add(new BatchConverter.BatchItem(
                    item.file().toString(), InputSource.of(item.file()), fileName, target,
                    OutputSink.of(outPath), pm));
        }

        batch.onComplete(r -> {
            Platform.runLater(() -> {
                // 通过 label(=file path) 匹配回对应 item 更新状态
                ConversionItem item = items.stream()
                        .filter(i -> i.file().toString().equals(r.label())).findFirst().orElse(null);
                if (item == null) return;
                if (r.success()) {
                    item.setStatus(ConversionItem.Status.DONE);
                    item.setProgress(1.0);
                    item.setMessage("→ 完成");
                } else {
                    item.setStatus(item.isCancelRequested() ? ConversionItem.Status.CANCELLED
                            : ConversionItem.Status.FAILED);
                    if (item.messageProperty().get().isEmpty()) item.setMessage(r.error());
                    else item.setMessage(item.messageProperty().get() + " | " + r.error());
                }
                int d = done.incrementAndGet();
                summary.setText("进度 " + d + "/" + total);
                if (d == total) finishBatch();
            });
        });

        currentBatch = batch;
        batch.start();
    }

    private void finishBatch() {
        startBtn.setDisable(targetFormat.getSelectionModel().isEmpty());
        cancelBtn.setDisable(true);
        pauseBtn.setDisable(true);
        pauseBtn.setText("暂停");
        long ok = items.stream().filter(i -> i.status() == ConversionItem.Status.DONE).count();
        long fail = items.stream().filter(i -> i.status() == ConversionItem.Status.FAILED).count();
        long can = items.stream().filter(i -> i.status() == ConversionItem.Status.CANCELLED).count();
        summary.setText("完成: " + ok + " 成功, " + fail + " 失败, " + can + " 取消");
    }

    public void shutdown() {
        if (currentBatch != null) currentBatch.shutdownNow();
    }

    /** 应用设置到引擎和批量转换器。 */
    private void applySettings(SettingsDialog.Result r) {
        threadCount = r.threadCount();
        overwriteStrategy = r.overwriteStrategy();
        namingPattern = r.namingPattern();
        jpegQuality = r.jpegQuality();
        preserveMetadata = r.preserveMetadata();
        verifyOutput = r.verifyOutput();
        // 传递给引擎/批量转换器（若需要可扩展 BatchConverter 支持）
        // 目前在 startBatch 中使用 threadCount 等字段
    }

    // ------------------------------------------------------------ 列表单元格

    private static final class ItemCell extends ListCell<ConversionItem> {
        private final ProgressBar bar = new ProgressBar(0);
        private final Label name = new Label();
        private final Label status = new Label();
        private final Label message = new Label();
        private final HBox box;

        ItemCell() {
            bar.setPrefWidth(140);
            name.setMinWidth(200);
            status.setMinWidth(80);
            message.setStyle("-fx-text-fill: #666;");
            VBox right = new VBox(2, message, bar);
            HBox.setHgrow(right, Priority.ALWAYS);
            box = new HBox(10, name, status, right);
            box.setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(ConversionItem item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                return;
            }
            name.setText(item.file().getFileName().toString());
            status.textProperty().bind(item.statusProperty());
            message.textProperty().bind(item.messageProperty());
            bar.progressProperty().bind(item.progressProperty());
            String color = switch (item.status()) {
                case DONE -> "#2e7d32";
                case FAILED -> "#c62828";
                case CANCELLED -> "#f57f17";
                case RUNNING -> "#1565c0";
                default -> "#333";
            };
            status.setStyle("-fx-text-fill: " + color + ";");
            setGraphic(box);
        }
    }
}
