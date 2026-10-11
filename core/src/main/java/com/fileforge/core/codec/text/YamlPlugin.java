package com.fileforge.core.codec.text;

import com.fileforge.core.model.TextContent;
import com.fileforge.core.spi.Capability;
import com.fileforge.core.spi.ConversionException;
import com.fileforge.core.spi.FormatFamily;
import com.fileforge.core.spi.FormatPlugin;
import com.fileforge.core.spi.InputSource;
import com.fileforge.core.spi.Model;
import com.fileforge.core.spi.OutputSink;
import com.fileforge.core.spi.ProgressMonitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** YAML 插件（Jackson YAML，LOSSLESS）。 */
public final class YamlPlugin implements FormatPlugin {

    @Override public String id() { return "yaml.codec"; }
    @Override public FormatFamily family() { return FormatFamily.TEXT_CONTENT; }
    @Override public Set<String> extensions() { return Set.of("yaml", "yml"); }
    @Override public String mimeType() { return "application/yaml"; }
    @Override public Capability capability() { return Capability.LOSSLESS; }

    @Override
    public boolean canDecode(byte[] header) {
        if (header == null || header.length < 4) return false;
        // 严格：必须以 YAML 文档开始标记 --- 开头，或明确的 key: value 格式（首行）
        String s = new String(header).trim();
        if (s.startsWith("---")) return true;
        // 首行包含冒号且无其他格式特征
        int nl = s.indexOf('\n');
        String firstLine = nl >= 0 ? s.substring(0, nl) : s;
        return firstLine.matches("^[^:]+:\\s*.+") && !firstLine.contains("<") && !firstLine.contains("{");
    }

    @Override
    public Model decode(InputSource source, ProgressMonitor pm) throws ConversionException {
        try (InputStream in = source.openStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return TextCodecUtil.yamlToText(text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConversionException("YAML 读取失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supportsEncode(Class<? extends Model> modelType) {
        return TextContent.class.isAssignableFrom(modelType) || com.fileforge.core.model.DataTable.class.isAssignableFrom(modelType);
    }

    @Override
    public void encode(Model model, OutputSink sink, ProgressMonitor pm) throws ConversionException {
        try (OutputStream out = sink.openStream()) {
            String yaml;
            if (model instanceof TextContent tc) {
                yaml = TextCodecUtil.toYaml(TextCodecUtil.parseYaml(tc.text()));
            } else if (model instanceof com.fileforge.core.model.DataTable dt) {
                yaml = TextCodecUtil.toYaml(TextCodecUtil.tableToJsonText(dt));
            } else {
                throw new ConversionException("YAML 编码不支持模型: " + model.getClass());
            }
            out.write(yaml.getBytes(StandardCharsets.UTF_8));
            pm.progress(100);
        } catch (IOException e) {
            throw new ConversionException("YAML 写入失败: " + e.getMessage(), e);
        }
    }
}