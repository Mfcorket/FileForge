# FileForge — AGENTS.md

## 项目
开源、本地离线、跨平台文件格式转换工具。完整规划见 `A:\Doc\fileforge-plan.md`。

## 结构
- `core/` — Java 21 转换引擎（M1-M4 + P3-P7 已完成）：插件 SPI、中间模型、检测器、转换矩阵 + 模型适配、25 个格式插件
- `desktop/` — JavaFX GUI：拖拽、批量队列、动态目标格式、进度、取消/重试、预览、暂停/恢复
- `native/` — JNI 骨架（CMake + 桥接头 + 桥接实现，预留 FFmpeg 直连）；`android/` 未创建

## 约束（来自规划 §2.2）
- core 仅用纯 Java API；不使用外部命令行引擎；可用纯 Java 开源库
- 依赖方向：desktop/android → core → native(JNI)，禁止反向

## 构建与测试
本机无 Gradle 命令行，Gradle 8.10.2 已解压在 `E:\Run Flie\TEMP\opencode\gradle-8.10.2`（临时目录，可能被清理）。
本机内存/页面文件极小，Gradle 常因虚拟内存不足失败；可靠验证方式是 javac 直编（53 项测试）：

```powershell
# 编译 + 跑 core 冒烟测试（53 项）
$CP = (Get-ChildItem core\libs -Filter *.jar | % FullName) -join ';'
javac -d out --release 21 -cp $CP (Get-ChildItem -Recurse -Filter *.java core\src\main | % FullName)
javac -d out -cp "out;$CP" (Get-ChildItem -Recurse -Filter *.java core\src\test | % FullName)
Copy-Item -Recurse core\src\main\resources\* out -Force
java -Dfile.encoding=UTF-8 -cp "out;$CP" com.fileforge.core.CoreSmokeTest   # stdout 会混入 ffmpeg 日志，过滤 [PASS]/[FAIL]

# 运行桌面端（JavaFX SDK 在 D:\Application\Compiler\Java\Zulu\javafx-sdk-21.0.12）
javac -d out --release 21 --module-path <JFX_SDK>\lib --add-modules javafx.controls -cp "out;$CP" (Get-ChildItem -Recurse -Filter *.java desktop\src\main | % FullName)
java --module-path <JFX_SDK>\lib --add-modules javafx.controls -cp "out;$CP" com.fileforge.desktop.MainApp

# CLI 批量转换（无 UI）
java -Dfile.encoding=UTF-8 -cp "out;$CP" com.fileforge.desktop.CliMain --to png a.pdf b.ai c.bmp
```

Gradle 构建（内存充足时）：
```powershell
$env:JAVA_HOME="D:\Application\Compiler\Java\Zulu\21"
gradle build   # toolchain 路径已在 gradle.properties 配置
```

## 插件清单（25 个，META-INF/services）
| 族 | 插件 |
|---|---|
| 图片 | png / bmp（自研 LOSSLESS）；jpeg / gif（ImageIO LOSSY）；webp / tiff（ImageIO）；svg（自研） |
| 文档 | pdf·ai（PDFBox，BEST_EFFORT 解码/编码 DocumentLayout） |
| 文本/数据 | json / yaml / xml / csv / encoding.convert（Jackson + ICU） |
| 归档 | zip / tar / gz（读写）；7z（仅解压）（Commons Compress） |
| Office | docx / xlsx / pptx（POI，仅解码；依赖 jar 需 Gradle 统一版本）|
| 字体/3D | ttf·otf（java.awt）；obj / stl / gltf·glb（自研） |
| 音频 | wav·aiff·au（javax.sound，读写）；mp3（JavaCV，读写）；flac（JavaCV，读写） |
| 视频 | mp4·mkv·webm·mov·avi（JavaCV，解码→帧+编码 mp4/mkv；引擎支持抽首帧导出 PNG/JPG/SVG） |

新增插件 = 实现 FormatPlugin + 在 `core/src/main/resources/META-INF/services/com.fileforge.core.spi.FormatPlugin` 注册。

## 依赖（core/libs/*.jar，手动下载；Gradle 下应改为仓库声明）
- PDFBox 3.0.5（pdfbox/fontbox/pdfbox-io/commons-logging）
- Jackson 2.17.1（core/annotations/databind/yaml/xml/csv + stax2/woodstox + snakeyaml）
- Commons Compress 1.26.1 + commons-io/xz/snappy
- ICU4J 75.1；POI 5.3.0 + xmlbeans 3.1.0 + ooxml-schemas 1.4 + commons-collections/math3/curvesapi + log4j2
- JavaCV 1.5.10（javacv/javacpp + ffmpeg-6.1.1 含 windows-x86_64 natives）

## 关键架构点
- ImageIO/AWT/javax.sound/java.awt 插件依赖 java.desktop，Android 端需替换实现。
- JavaCV 仅在方法体内引用（类无静态链接依赖），classpath 缺 natives 时插件可加载、编辑时抛 ConversionException；natives 从 jar 自动提取（无需系统装 FFmpeg）。
- 视频帧导出通过 ConversionEngine 的模型适配实现：VideoStream → 抽首帧 → RasterImage → 图片编码器。
- 多跳转换：FormatPlugin.producedModel() + ModelAdapter（VideoStream→RasterImage、DataTable↔TextContent、RasterImage→VectorDocument 等）+ ConversionMatrix BFS 寻路 + 引擎自动应用适配器链。
- BEST_EFFORT 警告：ProgressMonitor.warning() + CollectingProgressMonitor。
- 批量转换：BatchConverter（虚拟线程池 + ProgressMonitor.isCancelled + pause/resume + onComplete 回调）。