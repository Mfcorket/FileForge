package com.fileforge.desktop;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.prefs.Preferences;

/**
 * 设置对话框：线程数、覆盖策略、命名规则、转换参数等。
 * 持久化到用户偏好。
 */
public final class SettingsDialog extends Dialog<SettingsDialog.Result> {

    private final Preferences prefs = Preferences.userNodeForPackage(SettingsDialog.class);

    private final ComboBox<Integer> threadCountBox = new ComboBox<>();
    private final ComboBox<OverwriteStrategy> overwriteBox = new ComboBox<>();
    private final TextField namingPatternField = new TextField();
    private final TextField jpegQualityField = new TextField();
    private final CheckBox preserveMetadataBox = new CheckBox("保留元数据 (EXIF/XMP 等)");
    private final CheckBox verifyOutputBox = new CheckBox("转换后校验文件完整性");

    public enum OverwriteStrategy {
        SKIP("跳过已存在"),
        OVERWRITE("覆盖"),
        RENAME("重命名 (追加序号)");

        private final String label;
        OverwriteStrategy(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public record Result(
            int threadCount,
            OverwriteStrategy overwriteStrategy,
            String namingPattern,
            int jpegQuality,
            boolean preserveMetadata,
            boolean verifyOutput
    ) {}

    public SettingsDialog(Window owner) {
        setTitle("设置");
        initOwner(owner);
        getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        getDialogPane().setMinWidth(480);
        buildContent();
        loadDefaults();
        setResultConverter(btn -> btn == ButtonType.OK ? createResult() : null);
    }

    private Result createResult() {
        int quality;
        try {
            quality = Integer.parseInt(jpegQualityField.getText().trim());
            if (quality < 1 || quality > 100) quality = 90;
        } catch (NumberFormatException e) {
            quality = 90;
        }
        Result r = new Result(
                threadCountBox.getValue(),
                overwriteBox.getValue(),
                namingPatternField.getText().trim(),
                quality,
                preserveMetadataBox.isSelected(),
                verifyOutputBox.isSelected()
        );
        saveDefaults(r);
        return r;
    }

    private void buildContent() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        // 线程数
        grid.add(new Label("转换线程数:"), 0, 0);
        threadCountBox.getItems().addAll(1, 2, 4, 8, 16, 32);
        threadCountBox.setValue(4);
        grid.add(threadCountBox, 1, 0);

        // 覆盖策略
        grid.add(new Label("文件已存在时:"), 0, 1);
        overwriteBox.getItems().addAll(OverwriteStrategy.values());
        overwriteBox.setValue(OverwriteStrategy.RENAME);
        grid.add(overwriteBox, 1, 1);

        // 命名规则
        grid.add(new Label("命名模式:"), 0, 2);
        namingPatternField.setPromptText("${name}.${ext} (支持 ${name}, ${ext}, ${date}, ${seq})");
        namingPatternField.setText("${name}.${ext}");
        grid.add(namingPatternField, 1, 2);
        Tooltip.install(namingPatternField, new Tooltip(
                "${name} = 原文件名(不含扩展名)\n${ext} = 目标扩展名\n${date} = 日期(yyyyMMdd)\n${seq} = 序号(001, 002...)"));

        // JPEG 质量
        grid.add(new Label("JPEG 质量 (1-100):"), 0, 3);
        jpegQualityField.setText("90");
        jpegQualityField.setPrefWidth(80);
        grid.add(jpegQualityField, 1, 3);
        Tooltip.install(jpegQualityField, new Tooltip("仅对 JPEG 格式生效，100 为最高质量"));

        // 复选框
        VBox checks = new VBox(8, preserveMetadataBox, verifyOutputBox);
        grid.add(new Label("其它选项:"), 0, 4);
        grid.add(checks, 1, 4);
        preserveMetadataBox.setSelected(true);
        verifyOutputBox.setSelected(false);

        getDialogPane().setContent(new ScrollPane(grid));
        getDialogPane().setPrefSize(520, 420);
    }

    private void loadDefaults() {
        threadCountBox.setValue(prefs.getInt("threadCount", 4));
        overwriteBox.setValue(OverwriteStrategy.valueOf(prefs.get("overwriteStrategy", "RENAME")));
        namingPatternField.setText(prefs.get("namingPattern", "${name}.${ext}"));
        jpegQualityField.setText(String.valueOf(prefs.getInt("jpegQuality", 90)));
        preserveMetadataBox.setSelected(prefs.getBoolean("preserveMetadata", true));
        verifyOutputBox.setSelected(prefs.getBoolean("verifyOutput", false));
    }

    private void saveDefaults(Result r) {
        prefs.putInt("threadCount", r.threadCount());
        prefs.put("overwriteStrategy", r.overwriteStrategy().name());
        prefs.put("namingPattern", r.namingPattern());
        prefs.putInt("jpegQuality", r.jpegQuality());
        prefs.putBoolean("preserveMetadata", r.preserveMetadata());
        prefs.putBoolean("verifyOutput", r.verifyOutput());
    }

    /** 显示对话框并返回结果（若取消返回 null） */
    public static Result showAndWait(Window owner) {
        SettingsDialog d = new SettingsDialog(owner);
        return d.showAndWait().orElse(null);
    }
}