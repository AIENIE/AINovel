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
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
public class V2ExportRenderer {
    private final ObjectMapper objectMapper;

    public V2ExportRenderer(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public void render(String format, String snapshotJson, String configJson, String chapterRange, Path target) throws Exception {
        ManuscriptData data = collect(snapshotJson, configJson, chapterRange);
        String text = plainText(data);
        try (OutputStream output = Files.newOutputStream(target)) {
            switch (format) {
                case "txt" -> output.write(text.getBytes(data.charset()));
                case "docx" -> renderDocx(output, text, data);
                case "epub" -> renderEpub(output, text, data);
                case "pdf" -> output.write(renderPdf(text, data.title()));
                default -> throw new BusinessException("不支持的导出格式: " + format);
            }
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
            text.append(data.blocks().get(index));
        }
        return text.toString();
    }

    private void renderDocx(OutputStream output, String text, ManuscriptData data) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zipText(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            zipText(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
            for (String line : text.split("\\R", -1)) xml.append("<w:p><w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>");
            zipText(zip, "word/document.xml", xml.append("<w:sectPr/></w:body></w:document>").toString());
        }
    }

    private void renderEpub(OutputStream output, String text, ManuscriptData data) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry entry = new ZipEntry("mimetype"); entry.setMethod(ZipEntry.STORED); entry.setSize(mime.length);
            CRC32 crc = new CRC32(); crc.update(mime); entry.setCrc(crc.getValue()); zip.putNextEntry(entry); zip.write(mime); zip.closeEntry();
            zipText(zip, "META-INF/container.xml", "<?xml version=\"1.0\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            String body = Arrays.stream(text.split("\\R")).filter(line -> !line.isBlank()).map(line -> "<p>" + xml(line) + "</p>").reduce("", String::concat);
            zipText(zip, "OEBPS/chapter.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + xml(data.title()) + "</title></head><body>" + body + "</body></html>");
            zipText(zip, "OEBPS/nav.xhtml", "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><nav><ol><li><a href=\"chapter.xhtml\">正文</a></li></ol></nav></body></html>");
            zipText(zip, "OEBPS/content.opf", "<package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + xml(data.title()) + "</dc:title><dc:language>zh-CN</dc:language></metadata><manifest><item id=\"chap\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/></manifest><spine><itemref idref=\"chap\"/></spine></package>");
        }
    }

    private byte[] renderPdf(String text, String title) {
        String content = "BT /F1 12 Tf 50 800 Td (" + pdf(title) + ") Tj T* "
                + Arrays.stream(text.split("\\R")).map(line -> "(" + pdf(line) + ") Tj T* ").reduce("", String::concat) + "ET";
        List<String> objects = List.of("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n", "2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n", "3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>endobj\n", "4 0 obj<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>stream\n" + content + "\nendstream\nendobj\n", "5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n");
        StringBuilder pdf = new StringBuilder("%PDF-1.4\n"); List<Integer> offsets = new ArrayList<>(); offsets.add(0);
        for (String object : objects) { offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length); pdf.append(object); }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        return pdf.append("trailer<< /Size 6 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF").toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private int[] chapterRange(String range, int total) {
        if (total == 0 || range == null || !range.matches("\\d+(-\\d+)?")) return new int[]{1, total};
        String[] parts = range.split("-"); int first = Integer.parseInt(parts[0]); int last = parts.length == 1 ? first : Integer.parseInt(parts[1]);
        return new int[]{Math.max(1, Math.min(first, last)), Math.min(total, Math.max(first, last))};
    }
    private String normalize(String value) { return value == null ? "" : value.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "").replace("&nbsp;", " ").replace("&amp;", "&").trim(); }
    private String xml(String value) { return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;"); }
    private String pdf(String value) { String raw = value == null ? "" : value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)"); return raw.codePoints().map(cp -> cp <= 255 ? cp : '?').collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString(); }
    private void zipText(ZipOutputStream zip, String path, String text) throws Exception { zip.putNextEntry(new ZipEntry(path)); zip.write(text.getBytes(StandardCharsets.UTF_8)); zip.closeEntry(); }
    private record ManuscriptData(String title, String author, List<String> blocks, boolean includeMetadata, String lineEnding, Charset charset) { }
}
