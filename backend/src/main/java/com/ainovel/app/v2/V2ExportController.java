package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Outline;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/v2")
public class V2ExportController {
    private static final int MAX_CONCURRENT_JOBS = 3;
    private final V2AccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ConcurrentMap<UUID, Map<String, Object>> templates = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Map<String, Object>>> jobsByManuscript = new ConcurrentHashMap<>();

    public V2ExportController(V2AccessGuard accessGuard) {
        this.accessGuard = accessGuard;
        seedSystemTemplates();
    }

    @PostMapping("/manuscripts/{manuscriptId}/export")
    public Map<String, Object> createExportJob(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID manuscriptId,
                                               @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        cleanupExpiredJobs();

        String format = str(payload.get("format"), "txt").toLowerCase(Locale.ROOT);
        ensureSupportedFormat(format);
        if (countActiveJobs(user.getId()) >= MAX_CONCURRENT_JOBS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "单用户最多同时运行 3 个导出任务");
        }

        UUID templateId = payload.get("templateId") == null ? null : UUID.fromString(payload.get("templateId").toString());
        Map<String, Object> template = templateId == null ? null : templates.get(templateId);
        if (templateId != null && template == null) {
            throw new RuntimeException("导出模板不存在");
        }

        Object config = payload.get("config");
        if (!(config instanceof Map<?, ?>) && template != null) {
            config = template.get("config");
        }
        if (!(config instanceof Map<?, ?>)) {
            config = defaultConfig(format);
        }

        UUID jobId = UUID.randomUUID();
        Map<String, Object> job = new HashMap<>();
        job.put("id", jobId);
        job.put("userId", user.getId());
        job.put("storyId", manuscript.getOutline().getStory().getId());
        job.put("manuscriptId", manuscriptId);
        job.put("templateId", templateId);
        job.put("format", format);
        job.put("config", config);
        job.put("chapterRange", payload.getOrDefault("chapterRange", "all"));
        job.put("status", "queued");
        job.put("progress", 0);
        job.put("fileName", buildFileName(manuscript, format));
        job.put("filePath", null);
        job.put("fileSizeBytes", 0L);
        job.put("errorMessage", null);
        job.put("contentType", contentType(format));
        job.put("contentBytes", null);
        job.put("expiresAt", Instant.now().plus(24, ChronoUnit.HOURS));
        job.put("createdAt", Instant.now());
        job.put("startedAt", null);
        job.put("completedAt", null);

        jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).put(jobId, job);
        return job;
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs")
    public List<Map<String, Object>> listExportJobs(@AuthenticationPrincipal UserDetails principal,
                                                    @PathVariable UUID manuscriptId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        cleanupExpiredJobs();

        List<Map<String, Object>> jobs = new ArrayList<>(jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).values());
        jobs.forEach(job -> refreshJob(job, manuscript));
        jobs.sort((a, b) -> ((Instant) b.get("createdAt")).compareTo((Instant) a.get("createdAt")));
        return jobs;
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}")
    public Map<String, Object> getExportJob(@AuthenticationPrincipal UserDetails principal,
                                            @PathVariable UUID manuscriptId,
                                            @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        cleanupExpiredJobs();

        Map<String, Object> job = requireJob(manuscriptId, jobId);
        refreshJob(job, manuscript);
        return job;
    }

    @GetMapping("/manuscripts/{manuscriptId}/export/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal UserDetails principal,
                                           @PathVariable UUID manuscriptId,
                                           @PathVariable UUID jobId) {
        User user = accessGuard.currentUser(principal);
        Manuscript manuscript = accessGuard.requireOwnedManuscript(manuscriptId, user);
        cleanupExpiredJobs();

        Map<String, Object> job = requireJob(manuscriptId, jobId);
        refreshJob(job, manuscript);

        if (((Instant) job.get("expiresAt")).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "导出文件已过期");
        }
        if (!"completed".equalsIgnoreCase(str(job.get("status"), ""))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "导出任务尚未完成");
        }

        byte[] bytes = (byte[]) job.get("contentBytes");
        if (bytes == null || bytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.GONE, "导出文件不存在或已被清理");
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, str(job.get("contentType"), MediaType.APPLICATION_OCTET_STREAM_VALUE))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + str(job.get("fileName"), "export.txt") + "\"")
                .body(bytes);
    }

    @GetMapping("/export-templates")
    public List<Map<String, Object>> listTemplates(@AuthenticationPrincipal UserDetails principal) {
        User user = accessGuard.currentUser(principal);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> tpl : templates.values()) {
            if (tpl.get("userId") == null || Objects.equals(tpl.get("userId"), user.getId())) {
                out.add(tpl);
            }
        }
        return out;
    }

    @PostMapping("/export-templates")
    public Map<String, Object> createTemplate(@AuthenticationPrincipal UserDetails principal,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        String format = str(payload.get("format"), "txt").toLowerCase(Locale.ROOT);
        ensureSupportedFormat(format);

        Map<String, Object> tpl = new HashMap<>();
        tpl.put("id", UUID.randomUUID());
        tpl.put("userId", user.getId());
        tpl.put("name", str(payload.get("name"), "自定义模板"));
        tpl.put("description", str(payload.get("description"), ""));
        tpl.put("format", format);
        tpl.put("config", payload.getOrDefault("config", defaultConfig(format)));
        tpl.put("isDefault", bool(payload.get("isDefault"), false));
        tpl.put("createdAt", Instant.now());
        tpl.put("updatedAt", Instant.now());
        templates.put((UUID) tpl.get("id"), tpl);
        return tpl;
    }

    @PutMapping("/export-templates/{id}")
    public Map<String, Object> updateTemplate(@AuthenticationPrincipal UserDetails principal,
                                              @PathVariable UUID id,
                                              @RequestBody Map<String, Object> payload) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> tpl = requireTemplate(id);
        if (tpl.get("userId") == null || !Objects.equals(tpl.get("userId"), user.getId())) {
            throw new RuntimeException("无权修改该模板");
        }
        copyIfPresent(payload, tpl, "name", "description", "format");
        if (payload.containsKey("config")) tpl.put("config", payload.get("config"));
        if (payload.containsKey("isDefault")) tpl.put("isDefault", bool(payload.get("isDefault"), false));
        tpl.put("updatedAt", Instant.now());
        return tpl;
    }

    @DeleteMapping("/export-templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@AuthenticationPrincipal UserDetails principal,
                                               @PathVariable UUID id) {
        User user = accessGuard.currentUser(principal);
        Map<String, Object> tpl = requireTemplate(id);
        if (tpl.get("userId") == null || !Objects.equals(tpl.get("userId"), user.getId())) {
            throw new RuntimeException("无权删除该模板");
        }
        templates.remove(id);
        return ResponseEntity.noContent().build();
    }

    private void refreshJob(Map<String, Object> job, Manuscript manuscript) {
        String status = str(job.get("status"), "").toLowerCase(Locale.ROOT);
        if (List.of("completed", "failed", "expired", "cancelled").contains(status)) return;
        if (((Instant) job.get("expiresAt")).isBefore(Instant.now())) {
            expireJob(job);
            return;
        }

        Instant createdAt = (Instant) job.get("createdAt");
        long elapsed = Math.max(0, Duration.between(createdAt, Instant.now()).toMillis());
        if (elapsed < 1_000) {
            job.put("status", "queued");
            job.put("progress", 10);
            return;
        }
        if (elapsed < 3_000) {
            job.put("status", "processing");
            job.put("progress", 50);
            job.putIfAbsent("startedAt", Instant.now());
            return;
        }
        if (elapsed < 4_500) {
            job.put("status", "processing");
            job.put("progress", 85);
            job.putIfAbsent("startedAt", Instant.now());
            return;
        }

        try {
            byte[] bytes = generateFile(manuscript, job);
            job.put("contentBytes", bytes);
            job.put("fileSizeBytes", (long) bytes.length);
            job.put("filePath", "exports/" + manuscript.getId() + "/" + job.get("id") + "." + job.get("format"));
            job.put("status", "completed");
            job.put("progress", 100);
            job.put("completedAt", Instant.now());
            job.put("errorMessage", null);
        } catch (Exception ex) {
            job.put("status", "failed");
            job.put("progress", 100);
            job.put("completedAt", Instant.now());
            job.put("errorMessage", ex.getMessage() == null ? "导出失败" : ex.getMessage());
        }
    }

    private byte[] generateFile(Manuscript manuscript, Map<String, Object> job) throws Exception {
        String format = str(job.get("format"), "txt").toLowerCase(Locale.ROOT);
        Map<String, Object> cfg = map(job.get("config"));
        ManuscriptData data = collectData(manuscript, cfg, str(job.get("chapterRange"), "all"));
        String text = renderPlainText(data, cfg);

        return switch (format) {
            case "txt" -> text.getBytes(resolveCharset(cfg));
            case "docx" -> renderDocx(text, data.title, data.author);
            case "epub" -> renderEpub(text, data.title, data.author);
            case "pdf" -> renderPdf(text, data.title);
            default -> throw new RuntimeException("不支持的导出格式: " + format);
        };
    }

    private ManuscriptData collectData(Manuscript manuscript, Map<String, Object> cfg, String range) {
        Map<String, String> sections = parseSections(str(manuscript.getSectionsJson(), "{}"));
        Set<String> selectedSceneIds = parseSelectedSceneIds(cfg);

        List<String> blocks = new ArrayList<>();
        Outline outline = manuscript.getOutline();
        if (outline != null && outline.getContentJson() != null && !outline.getContentJson().isBlank()) {
            Map<String, Object> outlineJson = readMap(outline.getContentJson());
            List<Map<String, Object>> chapters = listOfMap(outlineJson.get("chapters"));
            int[] boundaries = parseChapterRange(range, chapters.size());
            for (int i = 0; i < chapters.size(); i++) {
                int order = i + 1;
                if (order < boundaries[0] || order > boundaries[1]) continue;
                Map<String, Object> chapter = chapters.get(i);
                String chapterTitle = str(chapter.get("title"), "第" + order + "章");
                blocks.add(chapterTitle);
                List<Map<String, Object>> scenes = listOfMap(chapter.get("scenes"));
                for (int j = 0; j < scenes.size(); j++) {
                    Map<String, Object> scene = scenes.get(j);
                    String sceneId = str(scene.get("id"), "");
                    if (!selectedSceneIds.isEmpty() && !selectedSceneIds.contains(sceneId)) continue;
                    String sceneTitle = str(scene.get("title"), "场景 " + (j + 1));
                    String sceneBody = normalizeText(sections.getOrDefault(sceneId, ""));
                    blocks.add(sceneTitle);
                    blocks.add(sceneBody);
                }
            }
        }

        if (blocks.isEmpty()) {
            sections.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                if (!selectedSceneIds.isEmpty() && !selectedSceneIds.contains(entry.getKey())) return;
                blocks.add(entry.getKey());
                blocks.add(normalizeText(entry.getValue()));
            });
        }

        return new ManuscriptData(str(manuscript.getTitle(), "AINovel 导出"), str(cfg.get("authorName"), "AINovel"), blocks);
    }

    private String renderPlainText(ManuscriptData data, Map<String, Object> cfg) {
        String lineEnding = "CRLF".equalsIgnoreCase(str(cfg.get("lineEnding"), "LF")) ? "\r\n" : "\n";
        String chapterSep = str(cfg.get("chapterSeparator"), "\n\n========\n\n").replace("\n", lineEnding);
        boolean includeMeta = bool(cfg.get("includeMetadata"), true);

        StringBuilder sb = new StringBuilder();
        if (includeMeta) {
            sb.append(data.title).append(lineEnding)
                    .append("作者: ").append(data.author).append(lineEnding)
                    .append("导出时间: ").append(Instant.now()).append(lineEnding).append(lineEnding);
        }
        for (int i = 0; i < data.blocks.size(); i++) {
            sb.append(data.blocks.get(i));
            if (i < data.blocks.size() - 1) {
                sb.append(lineEnding).append(lineEnding);
                if ((i % 2) == 1) sb.append(chapterSep);
            }
        }
        return sb.toString();
    }

    private byte[] renderDocx(String text, String title, String author) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + toWordP(title) + toWordP("作者: " + author)
                + Arrays.stream(text.split("\\R")).map(this::toWordP).reduce("", String::concat)
                + "<w:sectPr/></w:body></w:document>";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zipText(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
            zipText(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
            zipText(zip, "word/document.xml", xml);
        }
        return out.toByteArray();
    }

    private String toWordP(String line) {
        return "<w:p><w:r><w:t xml:space=\"preserve\">" + escapeXml(line) + "</w:t></w:r></w:p>";
    }

    private byte[] renderEpub(String text, String title, String author) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            byte[] mime = "application/epub+zip".getBytes(StandardCharsets.US_ASCII);
            ZipEntry mimeEntry = new ZipEntry("mimetype");
            mimeEntry.setMethod(ZipEntry.STORED);
            mimeEntry.setSize(mime.length);
            CRC32 crc = new CRC32();
            crc.update(mime);
            mimeEntry.setCrc(crc.getValue());
            zip.putNextEntry(mimeEntry);
            zip.write(mime);
            zip.closeEntry();

            zipText(zip, "META-INF/container.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><container version=\"1.0\" xmlns=\"urn:oasis:names:tc:opendocument:xmlns:container\"><rootfiles><rootfile full-path=\"OEBPS/content.opf\" media-type=\"application/oebps-package+xml\"/></rootfiles></container>");
            zipText(zip, "OEBPS/chapter.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>" + escapeXml(title) + "</title></head><body><h1>" + escapeXml(title) + "</h1><p>作者：" + escapeXml(author) + "</p>" + toHtmlParagraphs(text) + "</body></html>");
            zipText(zip, "OEBPS/nav.xhtml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><html xmlns=\"http://www.w3.org/1999/xhtml\"><body><nav><ol><li><a href=\"chapter.xhtml\">正文</a></li></ol></nav></body></html>");
            zipText(zip, "OEBPS/content.opf", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><package xmlns=\"http://www.idpf.org/2007/opf\" version=\"3.0\" unique-identifier=\"bookid\"><metadata xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:identifier id=\"bookid\">" + UUID.randomUUID() + "</dc:identifier><dc:title>" + escapeXml(title) + "</dc:title><dc:language>zh-CN</dc:language></metadata><manifest><item id=\"chap\" href=\"chapter.xhtml\" media-type=\"application/xhtml+xml\"/><item id=\"nav\" href=\"nav.xhtml\" media-type=\"application/xhtml+xml\" properties=\"nav\"/></manifest><spine><itemref idref=\"chap\"/></spine></package>");
        }
        return out.toByteArray();
    }

    private String toHtmlParagraphs(String text) {
        StringBuilder sb = new StringBuilder();
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) sb.append("<p>").append(escapeXml(line)).append("</p>");
        }
        return sb.toString();
    }

    private byte[] renderPdf(String text, String title) {
        StringBuilder stream = new StringBuilder();
        stream.append("BT /F1 12 Tf 50 800 Td ");
        stream.append("(").append(escapePdf(title)).append(") Tj T* ");
        for (String line : text.split("\\R")) {
            if (line.isBlank()) {
                stream.append("T* ");
            } else {
                stream.append("(").append(escapePdf(line)).append(") Tj T* ");
            }
        }
        stream.append("ET");

        String content = stream.toString();
        List<String> objs = new ArrayList<>();
        objs.add("1 0 obj<< /Type /Catalog /Pages 2 0 R >>endobj\n");
        objs.add("2 0 obj<< /Type /Pages /Kids [3 0 R] /Count 1 >>endobj\n");
        objs.add("3 0 obj<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 5 0 R >> >> /Contents 4 0 R >>endobj\n");
        objs.add("4 0 obj<< /Length " + content.getBytes(StandardCharsets.ISO_8859_1).length + " >>stream\n" + content + "\nendstream\nendobj\n");
        objs.add("5 0 obj<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>endobj\n");

        StringBuilder pdf = new StringBuilder("%PDF-1.4\n");
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        for (String obj : objs) {
            offsets.add(pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length);
            pdf.append(obj);
        }
        int xref = pdf.toString().getBytes(StandardCharsets.ISO_8859_1).length;
        pdf.append("xref\n0 ").append(objs.size() + 1).append("\n0000000000 65535 f \n");
        for (int i = 1; i < offsets.size(); i++) {
            pdf.append(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.get(i)));
        }
        pdf.append("trailer<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF");
        return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
    }

    private String escapePdf(String text) {
        String raw = text == null ? "" : text.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            out.append(c <= 255 ? c : '?');
        }
        return out.toString();
    }

    private void zipText(ZipOutputStream zip, String path, String text) throws Exception {
        ZipEntry entry = new ZipEntry(path);
        zip.putNextEntry(entry);
        zip.write(text.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private Charset resolveCharset(Map<String, Object> cfg) {
        String encoding = str(cfg.get("txtEncoding"), str(cfg.get("encoding"), "UTF-8"));
        try {
            return Charset.forName(encoding);
        } catch (Exception ex) {
            return StandardCharsets.UTF_8;
        }
    }

    private int countActiveJobs(UUID userId) {
        int count = 0;
        for (ConcurrentMap<UUID, Map<String, Object>> jobs : jobsByManuscript.values()) {
            for (Map<String, Object> job : jobs.values()) {
                if (!Objects.equals(job.get("userId"), userId)) continue;
                String status = str(job.get("status"), "").toLowerCase(Locale.ROOT);
                if (!List.of("completed", "failed", "expired", "cancelled").contains(status)) count++;
            }
        }
        return count;
    }

    private void cleanupExpiredJobs() {
        Instant now = Instant.now();
        for (ConcurrentMap<UUID, Map<String, Object>> jobs : jobsByManuscript.values()) {
            for (Map<String, Object> job : jobs.values()) {
                Instant expiresAt = (Instant) job.get("expiresAt");
                if (expiresAt != null && expiresAt.isBefore(now)) expireJob(job);
            }
        }
    }

    private void expireJob(Map<String, Object> job) {
        job.put("status", "expired");
        job.put("progress", 100);
        job.put("contentBytes", null);
        job.put("filePath", null);
        job.put("fileSizeBytes", 0L);
        job.put("completedAt", Instant.now());
    }

    private Map<String, Object> requireJob(UUID manuscriptId, UUID jobId) {
        Map<String, Object> job = jobsByManuscript.computeIfAbsent(manuscriptId, key -> new ConcurrentHashMap<>()).get(jobId);
        if (job == null) throw new RuntimeException("导出任务不存在");
        return job;
    }

    private Map<String, Object> requireTemplate(UUID id) {
        Map<String, Object> tpl = templates.get(id);
        if (tpl == null) throw new RuntimeException("导出模板不存在");
        return tpl;
    }

    private String buildFileName(Manuscript manuscript, String format) {
        String title = str(manuscript.getTitle(), "AINovel").replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
        if (title.isBlank()) title = "AINovel";
        return title + "-" + Instant.now().toEpochMilli() + "." + format;
    }

    private void seedSystemTemplates() {
        for (String format : List.of("txt", "docx", "epub", "pdf")) {
            Map<String, Object> tpl = new HashMap<>();
            tpl.put("id", UUID.randomUUID());
            tpl.put("userId", null);
            tpl.put("name", "系统默认 " + format.toUpperCase(Locale.ROOT));
            tpl.put("description", "系统预设模板");
            tpl.put("format", format);
            tpl.put("config", defaultConfig(format));
            tpl.put("isDefault", true);
            tpl.put("createdAt", Instant.now());
            tpl.put("updatedAt", Instant.now());
            templates.put((UUID) tpl.get("id"), tpl);
        }
    }

    private Map<String, Object> defaultConfig(String format) {
        return switch (format) {
            case "txt" -> Map.of("encoding", "UTF-8", "lineEnding", "LF", "includeMetadata", true);
            case "docx" -> Map.of("includeTitlePage", true, "includeToc", true);
            case "epub" -> Map.of("includeTitlePage", true, "includeToc", true);
            case "pdf" -> Map.of("includeTitlePage", true, "includeToc", true);
            default -> Map.of();
        };
    }

    private String contentType(String format) {
        return switch (format) {
            case "txt" -> "text/plain; charset=UTF-8";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "epub" -> "application/epub+zip";
            case "pdf" -> MediaType.APPLICATION_PDF_VALUE;
            default -> MediaType.APPLICATION_OCTET_STREAM_VALUE;
        };
    }

    private void ensureSupportedFormat(String format) {
        if (!List.of("txt", "docx", "epub", "pdf").contains(format)) {
            throw new RuntimeException("不支持的导出格式: " + format);
        }
    }

    private Set<String> parseSelectedSceneIds(Map<String, Object> cfg) {
        Object raw = cfg.get("selectedSceneIds");
        if (!(raw instanceof List<?> list)) return Set.of();
        Set<String> ids = new LinkedHashSet<>();
        for (Object item : list) {
            String value = str(item, "");
            if (!value.isBlank()) ids.add(value);
        }
        return ids;
    }

    private int[] parseChapterRange(String range, int total) {
        if (total <= 0 || range == null || range.isBlank() || "all".equalsIgnoreCase(range.trim())) return new int[] {1, total};
        String v = range.trim();
        if (v.matches("^\\d+$")) {
            int p = Math.max(1, Math.min(total, Integer.parseInt(v)));
            return new int[] {p, p};
        }
        if (v.matches("^\\d+-\\d+$")) {
            String[] parts = v.split("-");
            int a = Integer.parseInt(parts[0]);
            int b = Integer.parseInt(parts[1]);
            if (a > b) {
                int t = a;
                a = b;
                b = t;
            }
            return new int[] {Math.max(1, Math.min(total, a)), Math.max(1, Math.min(total, b))};
        }
        return new int[] {1, total};
    }

    private Map<String, String> parseSections(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return new LinkedHashMap<>();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> listOfMap(Object raw) {
        if (!(raw instanceof List<?> list)) return new ArrayList<>();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> row = new HashMap<>();
                for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) row.put(String.valueOf(e.getKey()), e.getValue());
                out.add(row);
            }
        }
        return out;
    }

    private String normalizeText(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.replaceAll("(?i)<br\\s*/?>", "\n")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replaceAll("<[^>]+>", "")
                .replace("\r\n", "\n")
                .trim();
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String... keys) {
        for (String key : keys) if (source.containsKey(key)) target.put(key, source.get(key));
    }

    private Map<String, Object> map(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) return new HashMap<>();
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<?, ?> e : m.entrySet()) if (e.getKey() != null) out.put(String.valueOf(e.getKey()), e.getValue());
        return out;
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }

    private String str(Object value, String fallback) {
        if (value == null) return fallback;
        String s = value.toString().trim();
        return s.isEmpty() ? fallback : s;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value == null) return fallback;
        if (value instanceof Boolean b) return b;
        return Boolean.parseBoolean(value.toString());
    }

    private static class ManuscriptData {
        final String title;
        final String author;
        final List<String> blocks;

        ManuscriptData(String title, String author, List<String> blocks) {
            this.title = title;
            this.author = author;
            this.blocks = blocks;
        }
    }
}
