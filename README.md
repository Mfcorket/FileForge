# FileForge

开源、本地离线、跨平台的文件格式转换工具。核心价值：无需商业软件即可完成专有格式导出与各类格式互转；全部本地处理，数据不出设备。

## 特性

- **本地离线**：文件不离开本机，隐私安全
- **专有格式导出**：.ai / PDF 渲染导出 PNG/JPG/SVG
- **批量转换**：队列化批量处理，支持进度 / 取消 / 暂停 / 重试
- **插件化架构**：25+ 格式插件，新增格式即插即用
- **跨平台**：Windows / macOS / Linux 桌面；Android 规划中

## 支持格式

| 族 | 格式 |
|---|---|
| 图片 | png · bmp · jpeg · gif · webp · tiff · svg |
| 文档 | pdf · ai（PDF 兼容） |
| 文本/数据 | json · yaml · xml · csv · 编码转换 |
| 归档 | zip · tar · gz · 7z(读) |
| Office | docx · xlsx · pptx（解码） |
| 字体/3D | ttf · otf · obj · stl · gltf/glb |
| 音频 | wav · aiff · au · mp3(读) · flac |
| 视频 | mp4 · mkv · webm · mov · avi（解码→帧导出图片） |

转换路径由**中间模型 + 模型适配器**自动推导（多跳转换），例如：`docx → text → DocumentLayout → pdf`、`json → DataTable → csv`、`mp4 → 首帧 → png`。

## 快速开始

### 桌面版

```powershell
# 直接运行（需 Java 21 + JavaFX，见 AGENTS.md）
java --module-path <JFX_SDK>\lib --add-modules javafx.controls -cp "out;$(join ';' core\libs\*.jar)" com.fileforge.desktop.MainApp
```

或使用 Gradle 构建原生安装包（含精简 JRE，无需用户装 Java）：

```powershell
gradle :desktop:jpackage
# 产物在 desktop/build/dist/FileForge/FileForge.exe
```

### 命令行

```bash
# 批量转换：把所有输入转成 PNG
fileforge --to png a.pdf b.ai c.bmp

# 指定输出目录
fileforge --to csv --out ./out data.xlsx

# 查看全部支持格式
fileforge --list-formats
```

## 构建

- Java 21 (LTS)，Gradle 8.10.2，JavaFX 21
- `gradle build`：编译 + 运行 53 项冒烟测试
- `gradle :desktop:jpackage`：生成原生安装包
- 依赖为 Maven Central 声明；CI 跨平台矩阵见 `.github/workflows/`

## 架构

```
输入文件 → Detector(魔数/扩展名) → FormatPlugin.decode → 中间模型 → 模型适配链 → FormatPlugin.encode → 输出
```

模块：`core/`（引擎 + 插件）→ `desktop/`（JavaFX UI + CLI）→ `native/`（JNI 骨架，预留 FFmpeg 直连）。依赖方向单向，core 不依赖任何 UI。

规划与路线图：`A:\Doc\fileforge-plan.md`、`ROADMAP.md`

## 许可证

Apache License 2.0，见 [LICENSE](LICENSE)。
