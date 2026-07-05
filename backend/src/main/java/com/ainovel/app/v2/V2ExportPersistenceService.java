package com.ainovel.app.v2;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ExportJob;
import com.ainovel.app.v2.model.V2ExportTemplate;
import com.ainovel.app.v2.repo.V2ExportJobRepository;
import com.ainovel.app.v2.repo.V2ExportTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class V2ExportPersistenceService {
    private final V2ExportTemplateRepository templateRepository;
    private final V2ExportJobRepository jobRepository;
    private final V2Json v2Json;

    public V2ExportPersistenceService(V2ExportTemplateRepository templateRepository,
                                      V2ExportJobRepository jobRepository,
                                      V2Json v2Json) {
        this.templateRepository = templateRepository;
        this.jobRepository = jobRepository;
        this.v2Json = v2Json;
    }

    @Transactional
    public List<Map<String, Object>> listTemplates(User user) {
        ensureSystemTemplates();
        return templateRepository.findByUserIdOrUserIsNull(user.getId()).stream()
                .map(this::templateMap)
                .toList();
    }

    @Transactional
    public Map<String, Object> createTemplate(User user, Map<String, Object> payload) {
        V2ExportTemplate template = new V2ExportTemplate();
        template.setUser(user);
        template.setName(str(payload.get("name"), "自定义模板"));
        template.setDescription(str(payload.get("description"), ""));
        template.setFormat(str(payload.get("format"), "txt").toLowerCase(Locale.ROOT));
        template.setConfigJson(v2Json.write(payload.getOrDefault("config", defaultConfig(template.getFormat()))));
        template.setDefaultTemplate(bool(payload.get("isDefault"), false));
        return templateMap(templateRepository.saveAndFlush(template));
    }

    @Transactional
    public Map<String, Object> updateTemplate(User user, UUID id, Map<String, Object> payload) {
        V2ExportTemplate template = requireTemplate(id);
        if (template.getUser() == null || !Objects.equals(template.getUser().getId(), user.getId())) {
            throw new BusinessException("无权修改该模板");
        }
        if (payload.containsKey("name")) template.setName(str(payload.get("name"), template.getName()));
        if (payload.containsKey("description")) template.setDescription(str(payload.get("description"), ""));
        if (payload.containsKey("format")) template.setFormat(str(payload.get("format"), template.getFormat()).toLowerCase(Locale.ROOT));
        if (payload.containsKey("config")) template.setConfigJson(v2Json.write(payload.get("config")));
        if (payload.containsKey("isDefault")) template.setDefaultTemplate(bool(payload.get("isDefault"), false));
        return templateMap(templateRepository.saveAndFlush(template));
    }

    @Transactional
    public void deleteTemplate(User user, UUID id) {
        V2ExportTemplate template = requireTemplate(id);
        if (template.getUser() == null || !Objects.equals(template.getUser().getId(), user.getId())) {
            throw new BusinessException("无权删除该模板");
        }
        templateRepository.delete(template);
    }

    @Transactional
    public Map<String, Object> createJob(User user, Manuscript manuscript, Map<String, Object> payload,
                                         String fileName, String contentType) {
        UUID templateId = payload.get("templateId") == null ? null : UUID.fromString(payload.get("templateId").toString());
        V2ExportTemplate template = templateId == null ? null : requireTemplate(templateId);
        Object config = payload.get("config");
        if (!(config instanceof Map<?, ?>) && template != null) {
            config = v2Json.map(template.getConfigJson());
        }
        String format = str(payload.get("format"), "txt").toLowerCase(Locale.ROOT);
        if (!(config instanceof Map<?, ?>)) {
            config = defaultConfig(format);
        }
        V2ExportJob job = new V2ExportJob();
        job.setUser(user);
        Story story = manuscript.getOutline() == null ? null : manuscript.getOutline().getStory();
        if (story == null) {
            throw new BusinessException("稿件缺少所属故事，无法导出");
        }
        job.setStory(story);
        job.setManuscript(manuscript);
        job.setTemplate(template);
        job.setFormat(format);
        job.setConfigJson(v2Json.write(config));
        job.setChapterRange(str(payload.get("chapterRange"), "all"));
        job.setStatus("queued");
        job.setProgress(0);
        job.setFileName(fileName);
        job.setFileSizeBytes(0L);
        job.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return jobMap(jobRepository.saveAndFlush(job), contentType);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listJobs(UUID manuscriptId) {
        return jobRepository.findByManuscriptIdOrderByCreatedAtDesc(manuscriptId).stream()
                .map(job -> jobMap(job, contentType(job.getFormat())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getJob(UUID manuscriptId, UUID jobId) {
        return jobMap(requireJob(manuscriptId, jobId), null);
    }

    @Transactional
    public Map<String, Object> updateJob(UUID manuscriptId, UUID jobId, Map<String, Object> patch) {
        V2ExportJob job = requireJob(manuscriptId, jobId);
        if (patch.containsKey("status")) job.setStatus(str(patch.get("status"), job.getStatus()));
        if (patch.containsKey("progress")) job.setProgress(intVal(patch.get("progress"), job.getProgress()));
        if (patch.containsKey("filePath")) job.setFilePath(str(patch.get("filePath"), null));
        if (patch.containsKey("fileSizeBytes")) job.setFileSizeBytes((long) intVal(patch.get("fileSizeBytes"), 0));
        if (patch.containsKey("errorMessage")) job.setErrorMessage(patch.get("errorMessage") == null ? null : patch.get("errorMessage").toString());
        if (patch.containsKey("expiresAt") && patch.get("expiresAt") instanceof Instant instant) job.setExpiresAt(instant);
        return jobMap(jobRepository.saveAndFlush(job), null);
    }

    @Transactional(readOnly = true)
    public int countActiveJobs(UUID userId) {
        int count = 0;
        for (V2ExportJob job : jobRepository.findByUserId(userId)) {
            if (!List.of("completed", "failed", "expired", "cancelled").contains(str(job.getStatus(), "").toLowerCase(Locale.ROOT))) {
                count++;
            }
        }
        return count;
    }

    @Transactional
    public void cleanupExpiredJobs() {
        for (V2ExportJob job : jobRepository.findByExpiresAtBeforeAndStatusNot(Instant.now(), "expired")) {
            job.setStatus("expired");
            job.setProgress(100);
            job.setFilePath(null);
            job.setFileSizeBytes(0L);
            jobRepository.save(job);
        }
    }

    private void ensureSystemTemplates() {
        boolean hasSystem = templateRepository.findByUserIdOrUserIsNull(UUID.randomUUID()).stream().anyMatch(t -> t.getUser() == null);
        if (hasSystem) {
            return;
        }
        for (String format : List.of("txt", "docx", "epub", "pdf")) {
            V2ExportTemplate template = new V2ExportTemplate();
            template.setUser(null);
            template.setName("系统默认 " + format.toUpperCase(Locale.ROOT));
            template.setDescription("系统预设模板");
            template.setFormat(format);
            template.setConfigJson(v2Json.write(defaultConfig(format)));
            template.setDefaultTemplate(true);
            templateRepository.save(template);
        }
        templateRepository.flush();
    }

    private V2ExportTemplate requireTemplate(UUID id) {
        return templateRepository.findById(id).orElseThrow(() -> new BusinessException("导出模板不存在"));
    }

    private V2ExportJob requireJob(UUID manuscriptId, UUID id) {
        return jobRepository.findByManuscriptIdAndId(manuscriptId, id).orElseThrow(() -> new BusinessException("导出任务不存在"));
    }

    private Map<String, Object> templateMap(V2ExportTemplate template) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", template.getId());
        out.put("userId", template.getUser() == null ? null : template.getUser().getId());
        out.put("name", template.getName());
        out.put("description", template.getDescription());
        out.put("format", template.getFormat());
        out.put("config", v2Json.map(template.getConfigJson()));
        out.put("isDefault", template.isDefaultTemplate());
        out.put("createdAt", template.getCreatedAt());
        out.put("updatedAt", template.getUpdatedAt());
        return out;
    }

    private Map<String, Object> jobMap(V2ExportJob job, String explicitContentType) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", job.getId());
        out.put("userId", job.getUser().getId());
        out.put("storyId", job.getStory().getId());
        out.put("manuscriptId", job.getManuscript().getId());
        out.put("templateId", job.getTemplate() == null ? null : job.getTemplate().getId());
        out.put("format", job.getFormat());
        out.put("config", v2Json.map(job.getConfigJson()));
        out.put("chapterRange", job.getChapterRange());
        out.put("status", job.getStatus());
        out.put("progress", job.getProgress());
        out.put("fileName", job.getFileName());
        out.put("filePath", job.getFilePath());
        out.put("fileSizeBytes", job.getFileSizeBytes() == null ? 0L : job.getFileSizeBytes());
        out.put("errorMessage", job.getErrorMessage());
        out.put("contentType", explicitContentType == null ? contentType(job.getFormat()) : explicitContentType);
        out.put("contentBytes", null);
        out.put("expiresAt", job.getExpiresAt());
        out.put("createdAt", job.getCreatedAt());
        out.put("startedAt", null);
        out.put("completedAt", null);
        return out;
    }

    private Map<String, Object> defaultConfig(String format) {
        return switch (format) {
            case "txt" -> Map.of("encoding", "UTF-8", "lineEnding", "LF", "includeMetadata", true);
            case "docx", "epub", "pdf" -> Map.of("includeTitlePage", true, "includeToc", true);
            default -> Map.of();
        };
    }

    private String contentType(String format) {
        return switch (format) {
            case "txt" -> "text/plain; charset=UTF-8";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "epub" -> "application/epub+zip";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private int intVal(Object value, int fallback) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private String str(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value == null ? fallback : Boolean.parseBoolean(value.toString());
    }
}
