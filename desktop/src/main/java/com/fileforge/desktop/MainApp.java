package com.fileforge.desktop;

import com.fileforge.core.engine.ConversionEngine;
import com.fileforge.core.engine.PluginRegistry;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/** FileForge 桌面端入口。 */
public class MainApp extends Application {

    private ConversionEngine engine;

    @Override
    public void init() {
        PluginRegistry registry = new PluginRegistry();
        registry.loadServicePlugins(); // META-INF/services 中的 PNG/BMP/JPEG/GIF
        engine = new ConversionEngine(registry);
    }

    @Override
    public void start(Stage stage) {
        MainController controller = new MainController(engine);
        stage.setTitle("FileForge v0.1 — 本地文件格式转换");
        stage.setScene(new Scene(controller.root(), 860, 560));
        stage.setMinWidth(720);
        stage.setMinHeight(480);
        stage.setOnCloseRequest(e -> controller.shutdown());
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
