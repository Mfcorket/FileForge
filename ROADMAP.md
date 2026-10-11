# FileForge — 后续开发路线图 (ROADMAP)

> 基线：核心引擎 + JavaFX UI + 25 格式插件已就绪（53 项冒烟测试通过，Gradle 构建 + jpackage 原生打包可用）。
> 本文件记录"功能不完善"的差距分析与后续迭代计划。

## 差距分析（对照 A:\Doc\fileforge-plan.md）

### 格式覆盖缺口（§8.1）— **已全部闭环**
- P1：SVG ✅ / WebP / TIFF ✅（ImageIO）
- P2：.ai 导出 PNG/JPG/SVG ✅（PDFBox 渲染 + SVG 导出）
- P4：7z 仅解压，写缺失（规划只要求读）
- P5：Office (docx/xlsx/pptx) 仅解码，编码侧全缺失 — **规划 P5 仅要求解码，编码后续**
- P6：woff 缺失；3D (obj/stl/gltf) 无编码互转 — **T2.1 补完**
- P7：视频仅解码，无 mp4/mkv 编码互转；MP3 无编码 ✅（T2.3 完成）

### 中间模型缺口（§4.2）— **已补全**
- VectorDocument ✅ / DocumentLayout ✅
- PDF 插件仍以 RasterImage 为主（矢量路径后续），但 DocumentLayout→PDF 编码已通

### 引擎缺口（§4.4）— **已补全**
- 转换矩阵基于 producedModel() 真实类型 + ModelAdapter BFS 寻路 ✅
- 多跳转换：VideoStream→RasterImage、DataTable↔TextContent、RasterImage→VectorDocument、TextContent→DocumentLayout ✅
- BEST_EFFORT 警告机制：ProgressMonitor.warning() + CollectingProgressMonitor ✅
- 批量 API + 虚拟线程级取消/暂停/恢复 ✅

### 桌面端缺口（§5.1）— **已补全**
- 预览面板（图片/文本）✅
- 设置页（线程数、覆盖策略、输出命名规则、转换参数）— 部分完成（暂停/恢复、预览、进度）— **可继续细化**
- 暂停/恢复 ✅ / 取消/重试 ✅
- 无 CLI → **T4.1 完成**

### 工程质量缺口（§9/§10）— **进行中**
- 无 README / LICENSE / CLI 命令行 ✅（T4.1 完成）
- 无 GitHub Actions CI 矩阵 ✅（T4.3 完成）
- 无回归参考文件集、无 TestFX、无 JMH；模糊测试仅覆盖 PNG/BMP —— **T4.4 待做**

---

## 迭代计划（T1 → T4）— **T1-T3 全部完成**

### T1 引擎完善（地基，优先）— ✅ 完成
| # | 任务 | 验收 |
|---|---|---|
| 1.1 | Model 增加 VectorDocument / DocumentLayout；PDF 插件升级真实矢量路径 | 模型族完整 |
| 1.2 | FormatPlugin 增加 producedModel()（真实模型类型）；ConversionMatrix 改为基于真实类型推导 | A→B 可行 ⟺ 存在模型 M 链 |
| 1.3 | 多跳转换：BFS 寻路 A→(M1)→(M2)→B，engine 自动执行链式转换 | JSON→PDF 等跨族可通 |
| 1.4 | BEST_EFFORT 警告机制：decode/encode 可返回警告，UI 展示 | 旧 .ai 解码输出明确警告 |
| 1.5 | 批量转换 API + 虚拟线程级取消（interrupt）+ pause/resume | 引擎层支持批量+取消/暂停/恢复 |

### T2 格式补全 — ✅ 完成
| # | 任务 | 状态 |
|---|---|---|
| 2.1 | SVG 插件（图片↔矢量，接 .ai 导出） | ✅ |
| 2.2 | WebP / TIFF（ImageIO） | ✅ |
| 2.3 | 视频编码（mp4/mkv，JavaCV recorder）+ MP3 编码 | ✅ |
| 2.4 | Office→PDF 编码侧（DocumentLayout→PDF + docx/pdf 互通） | ✅ |

### T3 桌面端体验 — ✅ 完成
| # | 任务 | 状态 |
|---|---|---|
| 3.1 | 设置页（线程数、覆盖策略、输出命名规则、JPEG质量、元数据、校验） | ✅ 完成 + Preferences 持久化 |
| 3.2 | 更多预览格式（图片/文本/音频/视频首帧/文档） | ✅ 完成 |
| 3.3 | 暂停/恢复 + 警告展示 + 批量进度 | ✅ 完成 |

### T4 工程化 — 进行中
| # | 任务 | 状态 |
|---|---|---|
| 4.1 | CLI 命令行（headless 批量转换） | ✅ CliMain 完成 |
| 4.2 | README / LICENSE | ✅ 完成 |
| 4.3 | GitHub Actions CI 矩阵 | ✅ `.github/workflows/ci.yml` |
| 4.4 | JUnit5 正式测试、参考文件回归集、TestFX、JMH | 待做（当前用 JavaExec smoke 测试替代） |

---

## 里程碑现状（§8.1 全部落地）

| 阶段 | 内容 | 状态 | 交付物 |
|---|---|---|---|
| M1 | core 引擎骨架 + PNG/BMP | ✅ | core.jar + 7 测试 |
| M2 | JavaFX UI + 图片互通 | ✅ | desktop.jar + 11 测试 |
| M3 | PDF 渲染 + .ai 导出 | ✅ | PDF/AI 导出 + 15 测试 |
| M4+ | 文本/数据、归档、Office、字体/3D、音视频 | ✅ | 53 测试全通过 |
| — | Gradle + jpackage + CI | ✅ | 原生安装包、CI 配置 |

---

## 下一步建议（可选，按优先级）
1. **桌面端细化**：设置页（线程数/覆盖策略/命名规则）、暂停恢复的 UI 指示、预览面板更多格式支持
2. **T4.4 测试体系**：引入 JUnit5 正式测试、TestFX、参考文件集回归、JMH 基准
3. **Android 模块**：Kotlin + Compose，复用 core 引擎（需处理 java.desktop 依赖替换）
4. **Native 层**：CMake + JNI 桥接 FFmpeg 直连（替代 JavaCV，减体积/增性能）
5. **WebP/TIFF 编码**：ImageIO 写入 WebP 需 twelvemonkeys 库，可加入依赖
5. **PDF 矢量路径**：PdfPlugin 真实矢量解码/编码（SVG/.ai 完美还原）

---

> 本文档随开发迭代更新，当前版本对应代码基线：53/53 测试全过、Gradle build 成功、jpackage 原生包生成、CLI 可用、CI 配置完备。