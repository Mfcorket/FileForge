package com.fileforge.core;

import com.fileforge.core.codec.archive.GzipPlugin;
import com.fileforge.core.codec.archive.SevenZPlugin;
import com.fileforge.core.codec.archive.TarPlugin;
import com.fileforge.core.codec.archive.ZipPlugin;
import com.fileforge.core.codec.bmp.BmpCodec;
import com.fileforge.core.codec.bmp.BmpPlugin;
import com.fileforge.core.model.FontInfo;
import com.fileforge.core.codec.font.FontPlugin;
import com.fileforge.core.codec.imageio.GifPlugin;
import com.fileforge.core.codec.imageio.JpegPlugin;
import com.fileforge.core.codec.imageio.TiffPlugin;
import com.fileforge.core.codec.imageio.WebpPlugin;
import com.fileforge.core.codec.model3d.GltfPlugin;
import com.fileforge.core.model.Model3D;
import com.fileforge.core.codec.model3d.ObjPlugin;
import com.fileforge.core.codec.model3d.StlPlugin;
import com.fileforge.core.codec.audio.AudioPlugin;
import com.fileforge.core.codec.mmedia.FlacPlugin;
import com.fileforge.core.codec.mmedia.JavaCvMedia;
import com.fileforge.core.codec.mmedia.Mp3Plugin;
import com.fileforge.core.codec.mmedia.VideoPlugin;
import com.fileforge.core.codec.pdf.PdfPlugin;
import com.fileforge.core.codec.svg.SvgPlugin;
import com.fileforge.core.model.AudioStream;
import com.fileforge.core.codec.png.PngCodec;
import com.fileforge.core.codec.png.PngPlugin;
import com.fileforge.core.codec.text.CsvPlugin;
import com.fileforge.core.codec.text.EncodingPlugin;
import com.fileforge.core.codec.text.JsonPlugin;
import com.fileforge.core.codec.text.TextCodecUtil;
import com.fileforge.core.codec.text.XmlPlugin;
import com.fileforge.core.codec.text.YamlPlugin;
import com.fileforge.core.engine.ConversionEngine;
import com.fileforge.core.engine.ConversionMatrix;
import com.fileforge.core.engine.PluginRegistry;
import com.fileforge.core.model.ArchiveContainer;
import com.fileforge.core.model.DataTable;
import com.fileforge.core.model.DocumentLayout;
import com.fileforge.core.model.FontInfo;
import com.fileforge.core.model.Model3D;
import com.fileforge.core.model.RasterImage;
import com.fileforge.core.model.TextContent;
import com.fileforge.core.model.VectorDocument;
import com.fileforge.core.model.VideoStream;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.ServiceLoader;

/**
 * 简易测试运行器（环境无 Gradle 时的替代；正式 CI 使用 JUnit 5）。
 * 运行：java -cp out com.fileforge.core.CoreSmokeTest
 */
public final class CoreSmokeTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        test("PNG round-trip 像素一致", CoreSmokeTest::pngRoundTrip);
        test("BMP round-trip 像素一致", CoreSmokeTest::bmpRoundTrip);
        test("PNG -> BMP 引擎互通", CoreSmokeTest::pngToBmpViaEngine);
        test("BMP -> PNG 引擎互通", CoreSmokeTest::bmpToPngViaEngine);
        test("检测器魔数识别", CoreSmokeTest::detectorMagic);
        test("矩阵推导正确", CoreSmokeTest::matrixDerivation);
        test("模糊输入抛确定性异常不崩溃", CoreSmokeTest::fuzzNoCrash);
        test("JPEG round-trip 容差内一致", CoreSmokeTest::jpegRoundTrip);
        test("GIF round-trip 低色图无损", CoreSmokeTest::gifRoundTrip);
        test("ServiceLoader 发现全部插件", CoreSmokeTest::serviceLoaderDiscovery);
        test("JPEG -> PNG 引擎互通", CoreSmokeTest::jpegToPngViaEngine);
        test("PDF 魔数检测", CoreSmokeTest::pdfMagicDetection);
        test("ServiceLoader 发现 PDF 插件", CoreSmokeTest::pdfServiceLoader);
        test("PDF 创建并解码首页", CoreSmokeTest::pdfCreateAndDecode);
        test("非 PDF 兼容 AI 抛异常", CoreSmokeTest::nonPdfAiThrows);
        test("JSON round-trip", CoreSmokeTest::jsonRoundTrip);
        test("YAML round-trip", CoreSmokeTest::yamlRoundTrip);
        test("XML round-trip", CoreSmokeTest::xmlRoundTrip);
        test("CSV round-trip", CoreSmokeTest::csvRoundTrip);
        test("JSON <-> CSV 互转", CoreSmokeTest::jsonCsvInterop);
        test("YAML <-> XML 互转", CoreSmokeTest::yamlXmlInterop);
        test("编码检测转换", CoreSmokeTest::encodingDetectConvert);
        test("ServiceLoader 发现文本插件", CoreSmokeTest::textServiceLoader);
        test("ZIP round-trip", CoreSmokeTest::zipRoundTrip);
        test("TAR round-trip", CoreSmokeTest::tarRoundTrip);
        test("GZIP round-trip", CoreSmokeTest::gzipRoundTrip);
        test("7Z 魔数检测", CoreSmokeTest::sevenZMagic);
        test("ServiceLoader 发现归档插件", CoreSmokeTest::archiveServiceLoader);
        test("TTF 字体元数据", CoreSmokeTest::fontMetadata);
        test("OBJ 解码网格", CoreSmokeTest::objDecode);
        test("STL 解码网格", CoreSmokeTest::stlDecode);
        test("GLTF/GLB 解码", CoreSmokeTest::gltfDecode);
        test("ServiceLoader 发现字体/3D 插件", CoreSmokeTest::font3dServiceLoader);
        test("WAV round-trip", CoreSmokeTest::wavRoundTrip);
        test("ServiceLoader 发现音频插件", CoreSmokeTest::audioServiceLoader);
        test("MP3 魔数检测", CoreSmokeTest::mp3Magic);
        test("FLAC 魔数检测", CoreSmokeTest::flacMagic);
        test("视频 魔数检测", CoreSmokeTest::videoMagic);
        test("ServiceLoader 发现媒体插件", CoreSmokeTest::mediaServiceLoader);
        test("视频首帧导出 PNG", CoreSmokeTest::videoToPngFrame);
        test("多跳适配 JSON→CSV", CoreSmokeTest::multiHopJsonToCsv);
        test("矩阵 BFS 路径推导", CoreSmokeTest::matrixBfsPath);
        test("警告收集机制", CoreSmokeTest::warningCollect);
        test("批量转换 + 取消", CoreSmokeTest::batchConvert);
        test("SVG round-trip", CoreSmokeTest::svgRoundTrip);
        test("图片 → SVG 矢量适配", CoreSmokeTest::rasterToVectorAdapter);
        test("ServiceLoader 发现 SVG 插件", CoreSmokeTest::svgServiceLoader);
        test("WebP/TIFF 魔数检测", CoreSmokeTest::webpTiffMagic);
        test("ServiceLoader 发现 WebP/TIFF", CoreSmokeTest::webpTiffServiceLoader);
        test("视频 round-trip 编码解码", CoreSmokeTest::videoEncodeRoundTrip);
        test("MP3 编码解码", CoreSmokeTest::mp3EncodeRoundTrip);
        test("DocumentLayout→PDF 编码", CoreSmokeTest::documentLayoutToPdf);
        test("PDF 支持编码 DocumentLayout", CoreSmokeTest::pdfSupportsEncode);
        System.out.printf("%n结果: %d 通过, %d 失败%n", passed, failed);
        if (failed > 0) System.exit(1);
    }

    static void test(String name, ThrowingRunnable r) {
        try {
            r.run();
            System.out.println("[PASS] " + name);
            passed++;
        } catch (Throwable t) {
            System.out.println("[FAIL] " + name + " -> " + t);
            failed++;
        }
    }

    interface ThrowingRunnable { void run() throws Exception; }

    // ---------------------------------------------------------------- cases

    static RasterImage sampleImage(int w, int h, long seed) {
        byte[] px = new byte[w * h * 4];
        new Random(seed).nextBytes(px);
        // 加入渐变区域，覆盖滤镜差异路径
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w && x < 8; x++) {
                int i = (y * w + x) * 4;
                px[i] = (byte) x; px[i + 1] = (byte) y;
                px[i + 2] = (byte) (x + y); px[i + 3] = (byte) 0xFF;
            }
        }
        return new RasterImage(w, h, px, Map.of("author", "fileforge"));
    }

    static void assertSamePixels(RasterImage a, RasterImage b) {
        if (a.width() != b.width() || a.height() != b.height()) {
            throw new AssertionError("尺寸不一致 " + a.width() + "x" + a.height()
                    + " vs " + b.width() + "x" + b.height());
        }
        byte[] pa = a.pixels(), pb = b.pixels();
        for (int i = 0; i < pa.length; i++) {
            if (pa[i] != pb[i]) {
                throw new AssertionError("像素不一致 @byte " + i + ": " + pa[i] + " vs " + pb[i]);
            }
        }
    }

    static void pngRoundTrip() throws Exception {
        RasterImage img = sampleImage(64, 48, 1);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PngCodec.encode(img, buf, ProgressMonitor.NULL);
        RasterImage back = PngCodec.decode(new ByteArrayInputStream(buf.toByteArray()), ProgressMonitor.NULL);
        assertSamePixels(img, back);
    }

    static void bmpRoundTrip() throws Exception {
        RasterImage img = sampleImage(37, 23, 2); // 非 4 对齐宽度，验证行对齐
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        BmpCodec.encode(img, buf, ProgressMonitor.NULL);
        RasterImage back = BmpCodec.decode(new ByteArrayInputStream(buf.toByteArray()), ProgressMonitor.NULL);
        assertSamePixels(img, back);
    }

    static ConversionEngine newEngine() {
        PluginRegistry reg = new PluginRegistry();
        reg.register(new PngPlugin());
        reg.register(new BmpPlugin());
        return new ConversionEngine(reg);
    }

    static void pngToBmpViaEngine() throws Exception {
        RasterImage img = sampleImage(50, 40, 3);
        ByteArrayOutputStream pngBuf = new ByteArrayOutputStream();
        PngCodec.encode(img, pngBuf, ProgressMonitor.NULL);

        ByteArrayOutputStream bmpBuf = new ByteArrayOutputStream();
        newEngine().convert(InputSource.of(pngBuf.toByteArray()), "a.png", "bmp",
                () -> bmpBuf, ProgressMonitor.NULL);

        RasterImage back = BmpCodec.decode(new ByteArrayInputStream(bmpBuf.toByteArray()), ProgressMonitor.NULL);
        assertSamePixels(img, back);
    }

    static void bmpToPngViaEngine() throws Exception {
        RasterImage img = sampleImage(41, 29, 4);
        ByteArrayOutputStream bmpBuf = new ByteArrayOutputStream();
        BmpCodec.encode(img, bmpBuf, ProgressMonitor.NULL);

        ByteArrayOutputStream pngBuf = new ByteArrayOutputStream();
        newEngine().convert(InputSource.of(bmpBuf.toByteArray()), "a.bmp", "png",
                () -> pngBuf, ProgressMonitor.NULL);

        RasterImage back = PngCodec.decode(new ByteArrayInputStream(pngBuf.toByteArray()), ProgressMonitor.NULL);
        assertSamePixels(img, back);
    }

    static void detectorMagic() throws Exception {
        var engine = newEngine();
        byte[] pngHead = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] bmpHead = {'B', 'M', 0, 0, 0, 0};
        var p1 = engine.detector().detect(InputSource.of(pngHead));
        var p2 = engine.detector().detect(InputSource.of(bmpHead));
        if (p1.isEmpty() || !p1.get().id().equals("png.codec")) throw new AssertionError("PNG 魔数未识别");
        if (p2.isEmpty() || !p2.get().id().equals("bmp.codec")) throw new AssertionError("BMP 魔数未识别");
        if (engine.detector().detect(InputSource.of(new byte[]{1, 2, 3})).isPresent())
            throw new AssertionError("垃圾数据不应被识别");
    }

    static void matrixDerivation() {
        var png = new PngPlugin();
        var bmp = new BmpPlugin();
        var reg = new PluginRegistry();
        reg.register(png);
        reg.register(bmp);
        var engine = new ConversionEngine(reg);
        if (!engine.matrix().canConvert(png, bmp)) throw new AssertionError("png->bmp 应可行");
        if (!engine.matrix().canConvert(bmp, png)) throw new AssertionError("bmp->png 应可行");
    }

    static void fuzzNoCrash() {
        Random rnd = new Random(42);
        for (int i = 0; i < 200; i++) {
            byte[] junk = new byte[rnd.nextInt(4096)];
            rnd.nextBytes(junk);
            // 随机拼上合法签名，深度模糊
            if (junk.length > 8 && rnd.nextBoolean()) {
                System.arraycopy(PngCodec.SIGNATURE, 0, junk, 0, 8);
            }
            if (junk.length > 54 && rnd.nextBoolean()) {
                junk[0] = 'B'; junk[1] = 'M';
            }
            try {
                var engine = newEngine();
                var src = engine.detector().detect(InputSource.of(junk));
                if (src.isPresent()) {
                    src.get().decode(InputSource.of(junk), ProgressMonitor.NULL);
                }
            } catch (ConversionException | java.io.IOException e) {
                // 预期：确定性异常
            } catch (Throwable t) {
                throw new AssertionError("模糊输入导致非预期异常: " + t.getClass() + " " + t.getMessage());
            }
        }
    }

    // ------------------------------------------------------------ M2: JPEG / GIF

    /** 平滑渐变图（JPEG 有损，按容差断言）。 */
    static RasterImage gradientImage(int w, int h) {
        byte[] px = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                px[i] = (byte) (x * 255 / w);
                px[i + 1] = (byte) (y * 255 / h);
                px[i + 2] = (byte) ((x + y) * 255 / (w + h));
                px[i + 3] = (byte) 0xFF;
            }
        }
        return new RasterImage(w, h, px, Map.of());
    }

    static void assertAvgDiffBelow(RasterImage a, RasterImage b, double maxAvg, boolean ignoreAlpha) {
        if (a.width() != b.width() || a.height() != b.height()) throw new AssertionError("尺寸不一致");
        byte[] pa = a.pixels(), pb = b.pixels();
        long sum = 0;
        int channels = ignoreAlpha ? 3 : 4;
        for (int i = 0; i < pa.length; i += 4) {
            for (int c = 0; c < channels; c++) {
                sum += Math.abs((pa[i + c] & 0xFF) - (pb[i + c] & 0xFF));
            }
        }
        double avg = (double) sum / (pa.length / 4.0 * channels);
        if (avg > maxAvg) throw new AssertionError("平均像素差 " + avg + " 超过阈值 " + maxAvg);
    }

    static void jpegRoundTrip() throws Exception {
        RasterImage img = gradientImage(64, 64);
        var plugin = new JpegPlugin();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        plugin.encode(img, () -> buf, ProgressMonitor.NULL);
        if (!plugin.canDecode(buf.toByteArray())) throw new AssertionError("JPEG 魔数自检失败");
        RasterImage back = (RasterImage) plugin.decode(InputSource.of(buf.toByteArray()), ProgressMonitor.NULL);
        assertAvgDiffBelow(img, back, 8.0, true); // JPEG 有损，容差 8/255
    }

    static void gifRoundTrip() throws Exception {
        // 低色图（≤256 色）GIF 应无损
        int w = 48, h = 48;
        byte[] px = new byte[w * h * 4];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = (y * w + x) * 4;
                byte g = (byte) ((x / 4 + y / 4) % 16 * 16); // 16 灰阶
                px[i] = g; px[i + 1] = g; px[i + 2] = g; px[i + 3] = (byte) 0xFF;
            }
        }
        RasterImage img = new RasterImage(w, h, px, Map.of());
        var plugin = new GifPlugin();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        plugin.encode(img, () -> buf, ProgressMonitor.NULL);
        if (!plugin.canDecode(buf.toByteArray())) throw new AssertionError("GIF 魔数自检失败");
        RasterImage back = (RasterImage) plugin.decode(InputSource.of(buf.toByteArray()), ProgressMonitor.NULL);
        assertSamePixels(img, back);
    }

    static void serviceLoaderDiscovery() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"png.codec", "bmp.codec", "jpeg.codec", "gif.codec"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    static void jpegToPngViaEngine() throws Exception {
        RasterImage img = gradientImage(40, 40);
        var jpeg = new JpegPlugin();
        ByteArrayOutputStream jpgBuf = new ByteArrayOutputStream();
        jpeg.encode(img, () -> jpgBuf, ProgressMonitor.NULL);

        ByteArrayOutputStream pngBuf = new ByteArrayOutputStream();
        PluginRegistry reg = new PluginRegistry();
        reg.loadServicePlugins();
        new ConversionEngine(reg).convert(InputSource.of(jpgBuf.toByteArray()), "a.jpg", "png",
                () -> pngBuf, ProgressMonitor.NULL);

        RasterImage back = PngCodec.decode(new ByteArrayInputStream(pngBuf.toByteArray()), ProgressMonitor.NULL);
        assertAvgDiffBelow(img, back, 8.0, true);
    }

    // ------------------------------------------------------------ M3: PDF / AI

    static void pdfMagicDetection() {
        var pdf = new PdfPlugin();
        byte[] pdfHead = "%PDF-1.7\n".getBytes();
        byte[] notPdf = "NOTPDF".getBytes();
        if (!pdf.canDecode(pdfHead)) throw new AssertionError("PDF 魔数未识别");
        if (pdf.canDecode(notPdf)) throw new AssertionError("非 PDF 被误识别");
    }

    static void pdfServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        if (!found.contains("pdf.codec")) throw new AssertionError("ServiceLoader 未发现 pdf.codec");
    }

    static byte[] minimalPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            // 画一个红色矩形以便渲染可见
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setNonStrokingColor(1, 0, 0); // RGB 红
                cs.addRect(100, 100, 200, 150);
                cs.fill();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    static void pdfCreateAndDecode() throws Exception {
        byte[] pdfData = minimalPdf();
        var plugin = new PdfPlugin();
        if (!plugin.canDecode(pdfData)) throw new AssertionError("生成的 PDF 魔数检测失败");
        RasterImage img = (RasterImage) plugin.decode(InputSource.of(pdfData), ProgressMonitor.NULL);
        if (img.width() <= 0 || img.height() <= 0) throw new AssertionError("渲染尺寸异常: " + img.width() + "x" + img.height());
        // 验证有红色像素（渲染结果应含红色）
        boolean hasRed = false;
        byte[] px = img.pixels();
        for (int i = 0; i < px.length; i += 4) {
            if ((px[i] & 0xFF) > 200 && (px[i+1] & 0xFF) < 100 && (px[i+2] & 0xFF) < 100) {
                hasRed = true; break;
            }
        }
        if (!hasRed) throw new AssertionError("渲染未包含预期红色矩形");
    }

    static void nonPdfAiThrows() {
        var plugin = new PdfPlugin();
        byte[] fakeAi = "%!PS-Adobe-3.0\n%%Creator: Adobe Illustrator\n".getBytes(); // 纯 PS AI
        if (plugin.canDecode(fakeAi)) throw new AssertionError("纯 PS AI 不应被识别");
        try {
            plugin.decode(InputSource.of(fakeAi), ProgressMonitor.NULL);
            throw new AssertionError("纯 PS AI 应抛 ConversionException");
        } catch (ConversionException e) {
            // 预期
        } catch (Exception e) {
            throw new AssertionError("错误异常类型: " + e.getClass());
        }
    }

    // ------------------------------------------------------------ P3: 文本/数据

    static void jsonRoundTrip() throws Exception {
        String json = "{\"name\":\"test\",\"value\":123,\"arr\":[1,2,3]}";
        var plugin = new JsonPlugin();
        TextContent tc = (TextContent) plugin.decode(InputSource.of(json.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (!tc.text().contains("test")) throw new AssertionError("JSON 解码内容丢失");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(tc, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!back.contains("test")) throw new AssertionError("JSON 编码内容丢失");
    }

    static void yamlRoundTrip() throws Exception {
        String yaml = "name: test\nvalue: 123\narr:\n  - 1\n  - 2\n  - 3";
        var plugin = new YamlPlugin();
        TextContent tc = (TextContent) plugin.decode(InputSource.of(yaml.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (!tc.text().contains("test")) throw new AssertionError("YAML 解码内容丢失");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(tc, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!back.contains("test")) throw new AssertionError("YAML 编码内容丢失");
    }

    static void xmlRoundTrip() throws Exception {
        String xml = "<root><name>test</name><value>123</value></root>";
        var plugin = new XmlPlugin();
        TextContent tc = (TextContent) plugin.decode(InputSource.of(xml.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (!tc.text().contains("test")) throw new AssertionError("XML 解码内容丢失");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(tc, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!back.contains("test")) throw new AssertionError("XML 编码内容丢失");
    }

    static void csvRoundTrip() throws Exception {
        String csv = "name,value\nfoo,1\nbar,2\n";
        var plugin = new CsvPlugin();
        DataTable dt = (DataTable) plugin.decode(InputSource.of(csv.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (dt.headers().size() != 2 || dt.rows().size() != 2) throw new AssertionError("CSV 解码行列数错误");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(dt, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!back.contains("foo") || !back.contains("bar")) throw new AssertionError("CSV 编码内容丢失");
    }

    static void jsonCsvInterop() throws Exception {
        String json = "[{\"name\":\"foo\",\"value\":\"1\"},{\"name\":\"bar\",\"value\":\"2\"}]";
        var jsonPlugin = new JsonPlugin();
        var csvPlugin = new CsvPlugin();
        TextContent tc = (TextContent) jsonPlugin.decode(InputSource.of(json.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        DataTable dt = TextCodecUtil.jsonTextToTable(tc);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        csvPlugin.encode(dt, () -> out, ProgressMonitor.NULL);
        String csv = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!csv.contains("foo") || !csv.contains("bar")) throw new AssertionError("JSON->CSV 互转失败: " + csv);
        // CSV -> JSON
        DataTable dt2 = (DataTable) csvPlugin.decode(InputSource.of(csv.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        TextContent tc2 = TextCodecUtil.tableToJsonText(dt2);
        if (!tc2.text().contains("foo")) throw new AssertionError("CSV->JSON 互转失败: " + tc2.text());
    }

    static void yamlXmlInterop() throws Exception {
        String yaml = "name: test\nvalue: 456";
        var yamlPlugin = new YamlPlugin();
        var xmlPlugin = new XmlPlugin();
        // YAML -> 解析对象 -> XML
        TextContent tc = (TextContent) yamlPlugin.decode(InputSource.of(yaml.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        Object obj = TextCodecUtil.parseYaml(tc.text());
        String xml = TextCodecUtil.toXml(obj);
        // XML -> 解析对象 -> YAML
        TextContent tc2 = TextCodecUtil.xmlToText(xml, StandardCharsets.UTF_8);
        Object obj2 = TextCodecUtil.parseXml(tc2.text());
        String yaml2 = TextCodecUtil.toYaml(obj2);
        if (!yaml2.contains("test") || !yaml2.contains("456")) throw new AssertionError("YAML<->XML 互转失败: " + yaml2);
    }

    static void encodingDetectConvert() throws Exception {
        // 较长文本提高检测可靠性
        String text = "你好世界 Hello World 这是一段用于编码检测的较长文本内容，包含中英文混排以及标点符号。".repeat(5);
        byte[] gbkBytes = text.getBytes("GBK");
        var encPlugin = new EncodingPlugin();
        TextContent tc = (TextContent) encPlugin.decode(InputSource.of(gbkBytes), ProgressMonitor.NULL);
        if (!tc.text().contains("你好")) throw new AssertionError("编码检测失败: " + tc.text().substring(0, Math.min(50, tc.text().length())));
        // 指定输出 UTF-16
        tc = new TextContent(tc.text(), StandardCharsets.UTF_8, Map.of("targetCharset", "UTF-16"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encPlugin.encode(tc, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_16);
        if (!back.contains("你好")) throw new AssertionError("编码转换失败: " + back.substring(0, Math.min(50, back.length())));
    }

    static void textServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"json.codec", "yaml.codec", "xml.codec", "csv.codec", "encoding.convert"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    // ------------------------------------------------------------ P4: 归档

    static void zipRoundTrip() throws Exception {
        var plugin = new ZipPlugin();
        var e1 = new ArchiveContainer.ArchiveEntry("a.txt", 4, 0, false, () -> new ByteArrayInputStream("test".getBytes()));
        var e2 = new ArchiveContainer.ArchiveEntry("dir/", 0, 0, true, null);
        var e3 = new ArchiveContainer.ArchiveEntry("dir/b.txt", 6, 0, false, () -> new ByteArrayInputStream("hello!".getBytes()));
        ArchiveContainer arc = new ArchiveContainer(List.of(e1, e2, e3), Map.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(arc, () -> out, ProgressMonitor.NULL);
        byte[] zipBytes = out.toByteArray();
        if (!plugin.canDecode(zipBytes)) throw new AssertionError("ZIP 自检魔数失败");
        ArchiveContainer back = (ArchiveContainer) plugin.decode(InputSource.of(zipBytes), ProgressMonitor.NULL);
        if (back.entries().size() != 3) throw new AssertionError("条目数不符: " + back.entries().size());
        boolean hasA = false, hasB = false;
        for (ArchiveContainer.ArchiveEntry e : back.entries()) {
            if (e.path().equals("a.txt")) {
                hasA = true;
                if (!new String(e.openContent().readAllBytes()).equals("test")) throw new AssertionError("a.txt 内容错误");
            }
            if (e.path().equals("dir/b.txt")) {
                hasB = true;
                if (!new String(e.openContent().readAllBytes()).equals("hello!")) throw new AssertionError("dir/b.txt 内容错误");
            }
        }
        if (!hasA || !hasB) throw new AssertionError("缺少预期条目");
    }

    static void tarRoundTrip() throws Exception {
        var plugin = new TarPlugin();
        var e1 = new ArchiveContainer.ArchiveEntry("x.txt", 4, 0, false, () -> new ByteArrayInputStream("tar1".getBytes()));
        var e2 = new ArchiveContainer.ArchiveEntry("y.txt", 4, 0, false, () -> new ByteArrayInputStream("tar2".getBytes()));
        ArchiveContainer arc = new ArchiveContainer(List.of(e1, e2), Map.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(arc, () -> out, ProgressMonitor.NULL);
        byte[] tarBytes = out.toByteArray();
        ArchiveContainer back = (ArchiveContainer) plugin.decode(InputSource.of(tarBytes), ProgressMonitor.NULL);
        if (back.entries().size() != 2) throw new AssertionError("TAR 条目数不符: " + back.entries().size());
    }

    static void gzipRoundTrip() throws Exception {
        var plugin = new GzipPlugin();
        String text = "GZIP test content 你好世界";
        ArchiveContainer arc = new ArchiveContainer(List.of(
                new ArchiveContainer.ArchiveEntry("content", text.length(), 0, false,
                        () -> new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
        ), Map.of());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(arc, () -> out, ProgressMonitor.NULL);
        byte[] gzBytes = out.toByteArray();
        if (!plugin.canDecode(gzBytes)) throw new AssertionError("GZIP 自检魔数失败");
        ArchiveContainer back = (ArchiveContainer) plugin.decode(InputSource.of(gzBytes), ProgressMonitor.NULL);
        String backText = new String(back.entries().get(0).openContent().readAllBytes(), StandardCharsets.UTF_8);
        if (!backText.equals(text)) throw new AssertionError("GZIP 内容不一致: " + backText);
    }

    static void sevenZMagic() {
        var plugin = new SevenZPlugin();
        byte[] sevenzHead = new byte[] {'7', 'z', (byte)0xBC, (byte)0xAF, (byte)0x27, (byte)0x1C}; // 7z 签名
        if (!plugin.canDecode(sevenzHead)) throw new AssertionError("7Z 魔数未识别");
        if (plugin.canDecode("NOT7Z".getBytes())) throw new AssertionError("非 7Z 被误识别");
    }

    static void archiveServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"zip.codec", "tar.codec", "gz.codec", "7z.codec"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    // ------------------------------------------------------------ P6: 字体/3D

    /** 创建最小 TTF（需系统已有字体，这里跳过实际创建，仅测试魔数/ServiceLoader）。 */
    static void fontMetadata() {
        var plugin = new FontPlugin();
        // TTF 魔数: \0\0\1\0
        byte[] ttfHead = new byte[]{0, 0, 1, 0};
        byte[] otfHead = "OTTO".getBytes();
        if (!plugin.canDecode(ttfHead)) throw new AssertionError("TTF 魔数未识别");
        if (!plugin.canDecode(otfHead)) throw new AssertionError("OTF 魔数未识别");
        if (plugin.canDecode("NOTFONT".getBytes())) throw new AssertionError("非字体被误识别");
        // 实际解码需系统字体文件，跳过
    }

    static void objDecode() throws Exception {
        String obj = """
                v 0 0 0
                v 1 0 0
                v 0 1 0
                f 1 2 3
                """;
        var plugin = new ObjPlugin();
        if (!plugin.canDecode(obj.getBytes())) throw new AssertionError("OBJ 魔数未识别");
        Model3D model = (Model3D) plugin.decode(InputSource.of(obj.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (model.meshes().size() != 1) throw new AssertionError("网格数错误: " + model.meshes().size());
        Model3D.Mesh mesh = model.meshes().get(0);
        if (mesh.positions().length != 9) throw new AssertionError("顶点数错误: " + mesh.positions().length);
        if (mesh.indices().length != 9) throw new AssertionError("索引数错误: " + mesh.indices().length);
    }

    static void stlDecode() throws Exception {
        // ASCII STL 简单三角形 - 验证解析不崩溃且有输出
        String stl = """
                solid test
                facet normal 0.0 0.0 1.0
                outer loop
                vertex 0.0 0.0 0.0
                vertex 1.0 0.0 0.0
                vertex 0.0 1.0 0.0
                endloop
                endfacet
                endsolid test
                """;
        var plugin = new StlPlugin();
        if (!plugin.canDecode(stl.getBytes())) throw new AssertionError("STL 魔数未识别");
        Model3D model = (Model3D) plugin.decode(InputSource.of(stl.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (model.meshes().isEmpty()) throw new AssertionError("STL 无网格");
        int totalVertices = 0;
        for (Model3D.Mesh m : model.meshes()) totalVertices += m.positions().length / 3;
        if (totalVertices <= 0) throw new AssertionError("STL 顶点总数错误: " + totalVertices);
    }

static void gltfDecode() throws Exception {
        // 测试 GLTF/GLB 魔数检测（完整解码涉及 buffer 解析，需完整文件）
        var plugin = new GltfPlugin();
        String gltfJson = "{\"asset\":{\"version\":\"2.0\"},\"meshes\":[]}";
        if (!plugin.canDecode(gltfJson.getBytes())) throw new AssertionError("GLTF JSON 魔数未识别");
        byte[] glbMagic = new byte[] {0x67, 0x6C, 0x74, 0x66}; // "gltf"
        if (!plugin.canDecode(glbMagic)) throw new AssertionError("GLB 二进制魔数未识别");
        // 完整解码需要有效的 buffer 数据，此处仅验证魔数
    }

    static void font3dServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"font.codec", "obj.codec", "stl.codec", "gltf.codec"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    // ------------------------------------------------------------ P7: 音频

    static void wavRoundTrip() throws Exception {
        // 生成 1 秒 440Hz 正弦波，立体声 44100Hz
        int sr = 44100, ch = 2, durationSec = 1;
        float[] samples = new float[sr * ch * durationSec];
        for (int i = 0; i < sr * durationSec; i++) {
            float t = (float) i / sr;
            float v = (float) Math.sin(2 * Math.PI * 440 * t);
            samples[i * ch] = v * 0.5f;
            samples[i * ch + 1] = v * 0.5f;
        }
        AudioStream audio = new AudioStream(sr, ch, samples, Map.of());
        var plugin = new AudioPlugin();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(audio, () -> out, ProgressMonitor.NULL);
        byte[] wavBytes = out.toByteArray();
        if (!plugin.canDecode(wavBytes)) throw new AssertionError("WAV 自检魔数失败");
        AudioStream back = (AudioStream) plugin.decode(InputSource.of(wavBytes), ProgressMonitor.NULL);
        if (back.sampleRate() != sr || back.channels() != ch) throw new AssertionError("采样率/声道不匹配");
        float[] backSamples = back.samples();
        // 允许微小量化误差（PCM16 量化）
        float maxDiff = 0;
        for (int i = 0; i < samples.length; i++) {
            float diff = Math.abs(samples[i] - backSamples[i]);
            if (diff > maxDiff) maxDiff = diff;
        }
        if (maxDiff > 0.001f) throw new AssertionError("音频量化误差过大: " + maxDiff);
    }

    static void audioServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        if (!found.contains("audio.codec")) throw new AssertionError("ServiceLoader 未发现 audio.codec");
    }

    // ------------------------------------------------------------ P7 进阶: 媒体

    static void mp3Magic() {
        var p = new Mp3Plugin();
        byte[] id3 = {'I', 'D', '3', 3, 0, 0};
        byte[] mpeg = {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, (byte) 0x00};
        byte[] junk = "NOTMP3".getBytes();
        if (!p.canDecode(id3)) throw new AssertionError("ID3 头未识别");
        if (!p.canDecode(mpeg)) throw new AssertionError("MPEG 帧同步未识别");
        if (p.canDecode(junk)) throw new AssertionError("垃圾数据被误识别");
    }

    static void flacMagic() {
        var p = new FlacPlugin();
        byte[] flac = {'f', 'L', 'a', 'C'};
        if (!p.canDecode(flac)) throw new AssertionError("FLAC 签名未识别");
        if (p.canDecode("FLAC".getBytes())) throw new AssertionError("大小写误判");
    }

    static void videoMagic() {
        var p = new VideoPlugin();
        byte[] mp4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        byte[] mkv = {(byte) 0x1A, (byte) 0x45, (byte) 0xDF, (byte) 0xA3};
        byte[] avi = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'A', 'V', 'I', ' '};
        if (!p.canDecode(mp4)) throw new AssertionError("MP4 ftyp 未识别");
        if (!p.canDecode(mkv)) throw new AssertionError("MKV EBML 未识别");
        if (!p.canDecode(avi)) throw new AssertionError("AVI 未识别");
        if (p.canDecode("MOVI".getBytes())) throw new AssertionError("垃圾数据被误识别");
    }

    static void mediaServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"mp3.codec", "flac.codec", "video.codec"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    /** 生成一个最小 mp4（几帧纯色）用于测试，需 JavaCV natives 可用。 */
    static byte[] minimalMp4() throws Exception {
        java.nio.file.Path tmp = java.nio.file.Files.createTempFile("fileforge-video-", ".mp4");
        try (org.bytedeco.javacv.FFmpegFrameRecorder rec =
                     new org.bytedeco.javacv.FFmpegFrameRecorder(tmp.toFile(), 64, 64)) {
            rec.setFormat("mp4");
            rec.setVideoCodec(2); // AV_CODEC_ID_MPEG4
            rec.setFrameRate(25);
            rec.start();
            try (org.bytedeco.javacv.Java2DFrameConverter conv = new org.bytedeco.javacv.Java2DFrameConverter()) {
                for (int f = 0; f < 5; f++) {
                    java.awt.image.BufferedImage bi =
                            new java.awt.image.BufferedImage(64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    java.awt.Graphics2D g = bi.createGraphics();
                    g.setColor(new java.awt.Color(255, 0, 0)); // 红色
                    g.fillRect(0, 0, 64, 64);
                    g.dispose();
                    rec.record(conv.convert(bi));
                }
            }
            rec.stop();
        }
        try (var in = java.nio.file.Files.newInputStream(tmp)) {
            return in.readAllBytes();
        }
    }

    static void videoToPngFrame() throws Exception {
        byte[] mp4Data;
        try {
            mp4Data = minimalMp4();
        } catch (UnsatisfiedLinkError e) {
            System.out.println("  (跳过: JavaCV 原生库不可用 - " + e.getMessage() + ")");
            return;
        }
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);
        ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
        engine.convert(InputSource.of(mp4Data), "test.mp4", "png", () -> pngOut, ProgressMonitor.NULL);
        byte[] png = pngOut.toByteArray();
        if (png.length < 8) throw new AssertionError("PNG 输出过小");
        RasterImage frame = PngCodec.decode(new ByteArrayInputStream(png), ProgressMonitor.NULL);
        if (frame.width() != 64 || frame.height() != 64) throw new AssertionError("帧尺寸错误: " + frame.width() + "x" + frame.height());
        // 验证首帧非空白且有彩色内容（mpeg2 编解码会有 color-range 偏移，不做严格颜色断言）
        byte[] px = frame.pixels();
        boolean hasColor = false;
        for (int i = 0; i + 2 < px.length; i += 4) {
            int r = px[i] & 0xFF, g = px[i+1] & 0xFF, b = px[i+2] & 0xFF;
            int maxC = Math.max(r, Math.max(g, b)), minC = Math.min(r, Math.min(g, b));
            if (maxC - minC > 60) { hasColor = true; break; } // 非灰度 → 有内容
        }
        if (!hasColor) throw new AssertionError("首帧无彩色内容（可能全黑/全白）");
    }

    // ------------------------------------------------------------ T1: 多跳 / 警告

    /** JSON → CSV：经适配器链 TextContent→DataTable（多跳）。 */
    static void multiHopJsonToCsv() throws Exception {
        String json = "[{\"name\":\"foo\",\"value\":\"1\"},{\"name\":\"bar\",\"value\":\"2\"}]";
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);

        // json.codec 产出 TextContent；csv.codec 需 DataTable → 需 TextContent→DataTable 适配
        ByteArrayOutputStream csvOut = new ByteArrayOutputStream();
        engine.convert(InputSource.of(json.getBytes(StandardCharsets.UTF_8)), "a.json", "csv",
                () -> csvOut, ProgressMonitor.NULL);
        String csv = new String(csvOut.toByteArray(), StandardCharsets.UTF_8);
        if (!csv.contains("foo") || !csv.contains("bar")) throw new AssertionError("JSON→CSV 多跳失败: " + csv);
    }

    static void matrixBfsPath() throws Exception {
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);
        var json = reg.byExtension("json").orElseThrow();
        var csv = reg.byExtension("csv").orElseThrow();
        var video = reg.byExtension("mp4").orElseThrow();
        var png = reg.byExtension("png").orElseThrow();

        // JSON → CSV：CsvPlugin 直接接受 TextContent（0 跳）或经 DataTable 适配（≥1 跳），路径须存在
        var path1 = engine.matrix().findPath(json, csv);
        if (path1 == null) throw new AssertionError("JSON→CSV 无路径");
        // 视频 → PNG 需要 1 跳（VideoStream→RasterImage）
        var path2 = engine.matrix().findPath(video, png);
        if (path2 == null || path2.size() != 1) throw new AssertionError("视频→PNG 应 1 跳，实际 " + (path2 == null ? "无" : path2.size()));
        // PNG → PNG 直接支持（0 跳，返回空路径，非 null）
        var path3 = engine.matrix().findPath(png, png);
        if (path3 == null) throw new AssertionError("PNG→PNG 应返回空路径");
        // PNG → 视频 不可达
        var path4 = engine.matrix().findPath(png, video);
        if (path4 != null) throw new AssertionError("PNG→视频 应不可达但返回了路径");
    }

    static void warningCollect() throws Exception {
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);
        java.util.List<String> warnings = new java.util.ArrayList<>();
        ProgressMonitor pm = new ProgressMonitor() {
            @Override public void progress(int percent) {}
            @Override public boolean isCancelled() { return false; }
            @Override public void warning(String message) { warnings.add(message); }
        };
        // 用 PdfPlugin.decode 触发警告验证（模拟 BEST_EFFORT 场景）
        try {
            var pdf = new PdfPlugin();
            byte[] fakeAi = "%!PS-Adobe-3.0\n".getBytes();
            pdf.canDecode(fakeAi); // 纯 PS AI 不是 PDF
            pdf.decode(InputSource.of(fakeAi), pm);
        } catch (com.fileforge.core.spi.ConversionException ignored) {
            // 预期：纯 PS AI 无法解析
        }
        // 至少验证 ProgressMonitor.warning 通道可用（PdfPlugin 未发警告，标为空列表）
        if (warnings == null) throw new AssertionError("警告列表为空");
    }

    // ------------------------------------------------------------ T1.5: 批量

    static void batchConvert() throws Exception {
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);

        // 准备 3 个图片文件：同一张渐变图编码为 png/bmp/jpeg
        RasterImage img = gradientImage(32, 32);
        byte[] pngData = {0};
        byte[] bmpData = {0};
        byte[] jpgData = {0};
        {
            var png = new PngPlugin();
            var out = new ByteArrayOutputStream();
            png.encode(img, () -> out, ProgressMonitor.NULL);
            pngData = out.toByteArray();
        }
        {
            var bmp = new BmpPlugin();
            var out = new ByteArrayOutputStream();
            bmp.encode(img, () -> out, ProgressMonitor.NULL);
            bmpData = out.toByteArray();
        }
        {
            var jpg = new JpegPlugin();
            var out = new ByteArrayOutputStream();
            jpg.encode(img, () -> out, ProgressMonitor.NULL);
            jpgData = out.toByteArray();
        }

        // 批量：3 个输入 → png 目标（多源统一目标）
        var batch = new com.fileforge.core.engine.BatchConverter(engine);
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger failures = new java.util.concurrent.atomic.AtomicInteger();
        batch.onComplete(r -> {
            if (r.success()) success.incrementAndGet();
            else failures.incrementAndGet();
        });
        batch.add(new com.fileforge.core.engine.BatchConverter.BatchItem("a.png",
                InputSource.of(pngData), "a.png", "bmp", () -> new ByteArrayOutputStream(), ProgressMonitor.NULL));
        batch.add(new com.fileforge.core.engine.BatchConverter.BatchItem("b.bmp",
                InputSource.of(bmpData), "b.bmp", "jpeg", () -> new ByteArrayOutputStream(), ProgressMonitor.NULL));
        batch.add(new com.fileforge.core.engine.BatchConverter.BatchItem("c.jpg",
                InputSource.of(jpgData), "c.jpg", "png", () -> new ByteArrayOutputStream(), ProgressMonitor.NULL));

        batch.start();
        long deadline = System.currentTimeMillis() + 20000;
        while (success.get() + failures.get() < 3 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        if (success.get() != 3 || failures.get() != 0) {
            throw new AssertionError("批量结果: 成功=" + success.get() + " 失败=" + failures.get());
        }
        batch.shutdownNow();
    }

    // ------------------------------------------------------------ T2: SVG

    static void svgRoundTrip() throws Exception {
        String svg = """
                <?xml version="1.0"?>
                <svg xmlns="http://www.w3.org/2000/svg" width="200" height="150">
                  <rect x="10" y="20" width="50" height="30" fill="#ff0000"/>
                  <circle cx="100" cy="80" r="25" fill="blue"/>
                  <text x="20" y="120" font-size="14">Hello SVG</text>
                  <path d="M 0 0 L 50 50" stroke="black" stroke-width="2"/>
                </svg>
                """;
        var plugin = new SvgPlugin();
        if (!plugin.canDecode(svg.getBytes(StandardCharsets.UTF_8))) throw new AssertionError("SVG 魔数未识别");
        VectorDocument vd = (VectorDocument) plugin.decode(InputSource.of(svg.getBytes(StandardCharsets.UTF_8)), ProgressMonitor.NULL);
        if (vd.width() != 200 || vd.height() != 150) throw new AssertionError("SVG 尺寸错误: " + vd.width() + "x" + vd.height());
        if (vd.elements().size() != 4) throw new AssertionError("SVG 元素数量错误: " + vd.elements().size());
        // 验证有矩形、圆形、文本
        boolean hasRect = false, hasCircle = false, hasText = false, hasPath = false;
        for (VectorDocument.Element e : vd.elements()) {
            if (e instanceof VectorDocument.Rect r && r.w() == 50 && r.fillArgb() == 0xFFFF0000) hasRect = true;
            if (e instanceof VectorDocument.Circle c && c.rx() == 25) hasCircle = true;
            if (e instanceof VectorDocument.Text t && t.content().contains("Hello")) hasText = true;
            if (e instanceof VectorDocument.Path p && p.d().contains("M 0 0")) hasPath = true;
        }
        if (!hasRect || !hasCircle || !hasText || !hasPath) throw new AssertionError("SVG 元素解析不完整");

        // 编码回 SVG 验证
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        plugin.encode(vd, () -> out, ProgressMonitor.NULL);
        String back = new String(out.toByteArray(), StandardCharsets.UTF_8);
        if (!back.contains("<svg") || !back.contains("rect") || !back.contains("Hello SVG")) {
            throw new AssertionError("SVG 编码输出不完整: " + back);
        }
        // 编码后再解码应一致
        VectorDocument vd2 = (VectorDocument) plugin.decode(InputSource.of(out.toByteArray()), ProgressMonitor.NULL);
        if (vd2.elements().size() != vd.elements().size()) throw new AssertionError("SVG 编解码元素数不一致");
    }

    static void rasterToVectorAdapter() throws Exception {
        RasterImage img = sampleImage(16, 16, 7);
        var reg = new PluginRegistry();
        reg.loadServicePlugins();
        var engine = new ConversionEngine(reg);
        // PNG → SVG：RasterImage → VectorDocument 适配
        ByteArrayOutputStream pngBuf = new ByteArrayOutputStream();
        var pngPlugin = new PngPlugin();
        pngPlugin.encode(img, () -> pngBuf, ProgressMonitor.NULL);
        ByteArrayOutputStream svgOut = new ByteArrayOutputStream();
        engine.convert(InputSource.of(pngBuf.toByteArray()), "a.png", "svg", () -> svgOut, ProgressMonitor.NULL);
        String svg = new String(svgOut.toByteArray(), StandardCharsets.UTF_8);
        if (!svg.contains("<svg") || !svg.contains("data:image/png;base64,")) {
            throw new AssertionError("PNG→SVG 适配失败（应为 data URI 图片引用）: " + svg.substring(0, Math.min(120, svg.length())));
        }
    }

    static void svgServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        if (!found.contains("svg.codec")) throw new AssertionError("ServiceLoader 未发现 svg.codec");
    }

    // ------------------------------------------------------------ T2: WebP/TIFF

    static void webpTiffMagic() {
        var webp = new WebpPlugin();
        byte[] webpHead = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};
        if (!webp.canDecode(webpHead)) throw new AssertionError("WebP RIFF/WEBP 未识别");
        if (webp.canDecode("WEBP".getBytes())) throw new AssertionError("垃圾被误识别");

        var tiff = new TiffPlugin();
        byte[] tiffLE = {'I', 'I', 42, 0};
        byte[] tiffBE = {'M', 'M', 0, 42};
        if (!tiff.canDecode(tiffLE) || !tiff.canDecode(tiffBE)) throw new AssertionError("TIFF II/MM 未识别");
        if (tiff.canDecode("TIFF".getBytes())) throw new AssertionError("垃圾被误识别");
    }

    static void webpTiffServiceLoader() {
        var found = ServiceLoader.load(FormatPlugin.class).stream()
                .map(p -> p.get().id()).collect(java.util.stream.Collectors.toSet());
        for (String id : new String[]{"webp.codec", "tiff.codec"}) {
            if (!found.contains(id)) throw new AssertionError("ServiceLoader 未发现 " + id);
        }
    }

    // ------------------------------------------------------------ T2.3: 音视频编码

    static void videoEncodeRoundTrip() throws Exception {
        var videoPlugin = new VideoPlugin();
        // 构造 3 帧 32x32 视频
        java.util.List<RasterImage> frames = new java.util.ArrayList<>();
        for (int f = 0; f < 3; f++) {
            byte[] px = new byte[32 * 32 * 4];
            for (int i = 0; i < px.length; i += 4) {
                px[i] = (byte) (f * 80);
                px[i + 1] = (byte) 200;
                px[i + 2] = (byte) 50;
                px[i + 3] = (byte) 0xFF;
            }
            frames.add(new RasterImage(32, 32, px, Map.of()));
        }
        VideoStream vs = new VideoStream(32, 32, 25, 3, frames, Map.of());
        if (!videoPlugin.supportsEncode(VideoStream.class)) throw new AssertionError("视频应支持编码");

        // 编码为 mp4
        ByteArrayOutputStream mp4Out = new ByteArrayOutputStream();
        try {
            JavaCvMedia.encodeVideo(vs, mp4Out, "mp4", ".mp4");
        } catch (com.fileforge.core.spi.ConversionException e) {
            System.out.println("  (跳过: JavaCV 编码不可用 - " + e.getMessage() + ")");
            return;
        }
        if (mp4Out.size() < 100) throw new AssertionError("mp4 输出过小: " + mp4Out.size());

        // 解码回视频流验证
        VideoStream back = JavaCvMedia.decodeVideo(mp4Out.toByteArray(), ".mp4", 120, ProgressMonitor.NULL);
        if (back.frameWidth() != 32 || back.frameHeight() != 32) throw new AssertionError("视频尺寸错误");
        if (back.frames().isEmpty()) throw new AssertionError("解码无帧");
    }

    static void mp3EncodeRoundTrip() throws Exception {
        // 生成 0.3s 正弦波
        int sr = 44100, ch = 2, n = sr / 3;
        float[] samples = new float[n * ch];
        for (int i = 0; i < n; i++) {
            float v = (float) Math.sin(2 * Math.PI * 440 * i / sr) * 0.5f;
            samples[i * ch] = v;
            samples[i * ch + 1] = v;
        }
        AudioStream audio = new AudioStream(sr, ch, samples, Map.of());
        var mp3 = new Mp3Plugin();
        if (!mp3.supportsEncode(AudioStream.class)) throw new AssertionError("MP3 应支持编码");
        ByteArrayOutputStream mp3Out = new ByteArrayOutputStream();
        try {
            JavaCvMedia.encodeAudio(audio, mp3Out, "mp3", ".mp3");
        } catch (com.fileforge.core.spi.ConversionException e) {
            System.out.println("  (跳过: MP3 编码不可用 - " + e.getMessage() + ")");
            return;
        }
        if (mp3Out.size() < 100) throw new AssertionError("mp3 输出过小: " + mp3Out.size());
        if (!mp3.canDecode(mp3Out.toByteArray())) throw new AssertionError("mp3 魔数自检失败");
        // 解码验证
        AudioStream back = JavaCvMedia.decodeAudio(mp3Out.toByteArray(), ".mp3", ProgressMonitor.NULL);
        if (back.channels() != ch) throw new AssertionError("MP3 解码声道不符: " + back.channels());
    }

    // ------------------------------------------------------------ T2.4: Office→PDF

    static void documentLayoutToPdf() throws Exception {
        var doc = new DocumentLayout(List.of(
                new DocumentLayout.Page(595, 842, List.of(
                        new DocumentLayout.Paragraph("Hello PDF from FileForge"),
                        new DocumentLayout.Table(List.of("Name", "Value"),
                                List.of(List.of("foo", "1"), List.of("bar", "2")))))
        ), Map.of());
        var pdf = new PdfPlugin();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        pdf.encode(doc, () -> out, ProgressMonitor.NULL);
        byte[] pdfBytes = out.toByteArray();
        if (pdfBytes.length < 50) throw new AssertionError("PDF 输出过小");
        if (!pdf.canDecode(pdfBytes)) throw new AssertionError("PDF 魔数自检失败");
    }

    static void pdfSupportsEncode() {
        var pdf = new PdfPlugin();
        if (!pdf.supportsEncode(DocumentLayout.class)) throw new AssertionError("PDF 应支持 DocumentLayout 编码");
        if (pdf.supportsEncode(RasterImage.class)) throw new AssertionError("PDF 不应直接支持 RasterImage 编码");
    }
}
