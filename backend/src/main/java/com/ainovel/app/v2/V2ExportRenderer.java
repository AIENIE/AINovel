package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import com.ainovel.app.v2.export.*;

@Component
public class V2ExportRenderer {
    private final ObjectMapper objectMapper;
    private final Map<String, ExportFormatRenderer> formats = Map.of(
            "txt", new TxtExportRenderer(), "docx", new DocxExportRenderer(),
            "epub", new EpubExportRenderer(), "pdf", new PdfExportRenderer());

    public V2ExportRenderer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void render(String format, String snapshotJson, String configJson, String chapterRange, Path target) throws Exception {
        ManuscriptData data = collect(snapshotJson, configJson, chapterRange);
        String text = plainText(data);
        ExportFormatRenderer renderer = formats.get(format);
        if (renderer == null) throw new BusinessException("不支持的导出格式: " + format);
        try (OutputStream output = Files.newOutputStream(target)) {
            renderer.render(new ExportDocument(data.title(), data.author(), text, data.charset()), output);
        }
    }

    private ManuscriptData collect(String snapshotJson, String configJson, String range) throws Exception {
        JsonNode snapshot = objectMapper.readTree(snapshotJson);
        Map<String, Object> config = objectMapper.readValue(configJson == null ? "{}" : configJson, new TypeReference<>() {});
        Map<String, String> sections = objectMapper.readValue(snapshot.path("sections").asText("{}"), new TypeReference<>() {});
        Set<String> selected = new LinkedHashSet<>();
        Object selectedRaw = config.get("selectedSceneIds");
        if (selectedRaw instanceof List<?> list) list.forEach(item -> selected.add(String.valueOf(item)));
        List<String> blocks = new ArrayList<>();
        JsonNode outline = objectMapper.readTree(snapshot.path("outline").asText("{}"));
        JsonNode chapters = outline.path("chapters");
        int[] boundaries = chapterRange(range, chapters.size());
        for (int chapterIndex = 0; chapterIndex < chapters.size(); chapterIndex++) {
            int order = chapterIndex + 1;
            if (order < boundaries[0] || order > boundaries[1]) continue;
            JsonNode chapter = chapters.get(chapterIndex);
            blocks.add(chapter.path("title").asText("第" + order + "章"));
            JsonNode scenes = chapter.path("scenes");
            for (int sceneIndex = 0; sceneIndex < scenes.size(); sceneIndex++) {
                JsonNode scene = scenes.get(sceneIndex);
                String sceneId = scene.path("id").asText("");
                if (!selected.isEmpty() && !selected.contains(sceneId)) continue;
                blocks.add(scene.path("title").asText("场景 " + (sceneIndex + 1)));
                blocks.add(normalize(sections.get(sceneId)));
            }
        }
        if (blocks.isEmpty()) sections.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .filter(entry -> selected.isEmpty() || selected.contains(entry.getKey()))
                .forEach(entry -> { blocks.add(entry.getKey()); blocks.add(normalize(entry.getValue())); });
        boolean includeMetadata = !Boolean.FALSE.equals(config.get("includeMetadata"));
        String lineEnding = "CRLF".equalsIgnoreCase(String.valueOf(config.getOrDefault("lineEnding", "LF"))) ? "\r\n" : "\n";
        Charset charset;
        try { charset = Charset.forName(String.valueOf(config.getOrDefault("encoding", "UTF-8"))); }
        catch (Exception ex) { charset = StandardCharsets.UTF_8; }
        return new ManuscriptData(snapshot.path("title").asText("AINovel 导出"),
                String.valueOf(config.getOrDefault("authorName", snapshot.path("author").asText("AINovel"))),
                blocks, includeMetadata, lineEnding, charset);
    }

    private String plainText(ManuscriptData data) {
        StringBuilder text = new StringBuilder();
        if (data.includeMetadata()) text.append(data.title()).append(data.lineEnding())
                .append("作者: ").append(data.author()).append(data.lineEnding())
                .append("导出时间: ").append(Instant.now()).append(data.lineEnding()).append(data.lineEnding());
        for (int index = 0; index < data.blocks().size(); index++) {
            if (index > 0) text.append(data.lineEnding()).append(data.lineEnding());
            text.append(data.blocks().get(index).replace("\n", data.lineEnding()));
        }
        return text.toString();
    }

    private int[] chapterRange(String range, int total) {
        if (total == 0 || range == null || !range.matches("\\d+(-\\d+)?")) return new int[]{1, total};
        String[] parts = range.split("-"); int first = Integer.parseInt(parts[0]); int last = parts.length == 1 ? first : Integer.parseInt(parts[1]);
        return new int[]{Math.max(1, Math.min(first, last)), Math.min(total, Math.max(first, last))};
    }
    private String normalize(String value) {
        return com.ainovel.app.common.text.RichTextProjector.project(value,
                com.ainovel.app.common.text.RichTextProjector.Policy.EXPORT_V1).text();
    }
    private record ManuscriptData(String title, String author, List<String> blocks, boolean includeMetadata, String lineEnding, Charset charset) { }
}
