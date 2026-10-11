package com.fileforge.core.codec.pdf;

import com.fileforge.core.model.RasterImage;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

/**
 * PDF / AI 兼容格式插件（Apache PDFBox 3.0，纯 Java）。
 * - 仅解码：将首页渲染为 RasterImage（150 DPI）。
 * - 支持 .pdf 与 PDF 兼容的 .ai（以 %PDF 开头）。
 * - 纯 PostScript .ai 抛 ConversionException（BEST_EFFORT 策略）。
 */
public final class PdfPlugin implements FormatPlugin {

    @Override public String id() { return "pdf.codec"; }
    @Override public FormatFamily family() { return FormatFamily.RASTER_IMAGE; }
    @Override public Set<String> extensions() { return Set.of("pdf", "ai"); }
    @Override public String mimeType() { return "application/pdf"; }
    @Override public Capability capability() { return Capability.BEST_EFFORT; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 5) return false;
        return header[0] == '%' && header[1] == 'P' && header[2] == 'D' && header[3] == 'F';
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            byte[] data = in.readAllBytes();
            if (!canDecode(data)) {
                throw new ConversionException("非 PDF 兼容格式（不是以 %PDF 开头），无法作为 AI 导出");
            }
            return renderFirstPage(data, pm);
        } catch (IOException e) {
            throw new ConversionException("PDF/AI 读取失败: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new ConversionException("PDF/AI 解析失败: " + e.getMessage(), e);
        }
    }

    private RasterImage renderFirstPage(byte[] data, ProgressMonitor pm) throws ConversionException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            int pages = doc.getNumberOfPages();
            if (pages == 0) throw new ConversionException("PDF 无页面");
            PDFRenderer renderer = new PDFRenderer(doc);
            BufferedImage bi = renderer.renderImageWithDPI(0, 150, org.apache.pdfbox.rendering.ImageType.RGB);
            pm.progress(90);
            return bufferedToRaster(bi, pages);
        } catch (IOException e) {
            throw new ConversionException("PDF 渲染失败: " + e.getMessage(), e);
        }
    }

    private static RasterImage bufferedToRaster(BufferedImage bi, int pages) {
        int w = bi.getWidth(), h = bi.getHeight();
        byte[] px = new byte[w * h * 4];
        int[] argb = bi.getRGB(0, 0, w, h, null, 0, w);
        for (int i = 0; i < argb.length; i++) {
            int v = argb[i], d = i * 4;
            px[d]     = (byte) (v >>> 16);
            px[d + 1] = (byte) (v >>> 8);
            px[d + 2] = (byte) v;
            px[d + 3] = (byte) (v >>> 24);
        }
        return new RasterImage(w, h, px, Map.of("pdfPages", String.valueOf(pages)));
    }

    @Override
    public boolean requiresDesktop() {
        return true;
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return com.fileforge.core.model.DocumentLayout.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        if (!(model instanceof com.fileforge.core.model.DocumentLayout layout)) {
            throw new ConversionException("PDF 编码需要 DocumentLayout，收到 " + model.getClass().getSimpleName());
        }
        try (var out = sink.openStream()) {
            PdfWriter.write(layout, out);
            pm.progress(100);
        } catch (java.io.IOException e) {
            throw new ConversionException("PDF 写入失败: " + e.getMessage(), e);
        }
    }
}