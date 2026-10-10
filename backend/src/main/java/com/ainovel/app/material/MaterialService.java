package com.ainovel.app.material;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.dto.*;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.model.MaterialUploadJob;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.material.repo.MaterialUploadJobRepository;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

@Service
public class MaterialService {
    @Autowired(required=false)
    private com.ainovel.app.material.evidence.MaterialEvidenceService materialEvidence;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.manuscript.ManuscriptContentService contents;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private final MaterialRepository materialRepository;
    private final MaterialUploadJobRepository uploadJobRepository;
    private final ResourceAccessGuard accessGuard;
    private final MaterialRetrievalService materialRetrievalService;
    private final ManuscriptRepository manuscriptRepository;
    private final ObjectMapper objectMapper;
    private final JsonColumnCodec jsonColumnCodec;
    private final MaterialFingerprintService fingerprints;

    @Autowired
    public MaterialService(
            MaterialRepository materialRepository,
            MaterialUploadJobRepository uploadJobRepository,
            ResourceAccessGuard accessGuard,
            MaterialRetrievalService materialRetrievalService,
            ManuscriptRepository manuscriptRepository,
            ObjectMapper objectMapper,
            JsonColumnCodec jsonColumnCodec,
            MaterialFingerprintService fingerprints
    ) {
        this.materialRepository = materialRepository;
        this.uploadJobRepository = uploadJobRepository;
        this.accessGuard = accessGuard;
        this.materialRetrievalService = materialRetrievalService;
        this.manuscriptRepository = manuscriptRepository;
        this.objectMapper = objectMapper;
        this.jsonColumnCodec = jsonColumnCodec;
        this.fingerprints = fingerprints;
    }

    @Transactional
    public MaterialDto create(User user, MaterialCreateRequest request) {
        Material material = new Material();
        material.setUser(user);
        material.setTitle(request.title());
        material.setType(request.type());
        material.setSummary(request.summary());
        material.setContent(request.content());
        material.setTagsJson(writeJson(request.tags()));
        material.setStatus("approved");
        material.setSource("MANUAL");
        materialRepository.saveAndFlush(material);
        fingerprints.update(material);
        materialRetrievalService.indexMaterial(user, material);
        return toDto(material);
    }

    public List<MaterialDto> list(User user) {
        assertOwner(user);
        return materialRepository.findByUser(user).stream().map(this::toDto).toList();
    }

    public MaterialDto get(UUID id) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new BusinessException("素材不存在"));
        assertOwner(material.getUser());
        return toDto(material);
    }

    @Transactional
    public MaterialDto update(UUID id, MaterialUpdateRequest request) {
        if (request == null) throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "MATERIAL_UPDATE_REQUIRED");
        Material material = materialRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("素材不存在"));
        assertOwner(material.getUser());
        boolean changed = request.content() != null && !Objects.equals(request.content(), material.getContent())
                || request.title() != null && !Objects.equals(request.title(), material.getTitle())
                || request.summary() != null && !Objects.equals(request.summary(), material.getSummary())
                || request.tags() != null && !Objects.equals(writeJson(request.tags()), material.getTagsJson())
                || request.type() != null && !Objects.equals(request.type(), material.getType())
                || request.entitiesJson() != null && !Objects.equals(request.entitiesJson(), material.getEntitiesJson());
        if (changed) {
            material.setContentVersion(material.getContentVersion() + 1);
            if (!"MANUAL".equals(material.getSource()) && !"rejected".equals(material.getStatus())) material.setStatus("pending");
        }
        if (request.title() != null) material.setTitle(request.title());
        if (request.type() != null) material.setType(request.type());
        if (request.summary() != null) material.setSummary(request.summary());
        if (request.content() != null) material.setContent(request.content());
        if (request.tags() != null) material.setTagsJson(writeJson(request.tags()));

        if (request.entitiesJson() != null) material.setEntitiesJson(request.entitiesJson());
        materialRepository.saveAndFlush(material);
        fingerprints.update(material);
        materialRetrievalService.indexMaterial(material.getUser(), material);
        return toDto(material);
    }

    @Transactional
    public void delete(UUID id) {
        Material material = materialRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("素材不存在"));
        assertOwner(material.getUser());
        materialRepository.delete(material);
        materialRetrievalService.deleteIndexAfterCommit(id);
    }

    @Transactional
    public FileImportJobDto createUploadJob(User user, String fileName, String content) {
        MaterialUploadJob job = new MaterialUploadJob();
        job.setFileName(fileName);
        assertOwner(user);
        job.setOwnerUserId(user.getId());
        job.setStatus("completed");
        job.setProgress(100);
        uploadJobRepository.save(job);
        // 立即创建一个待审素材
        Material material = new Material();
        material.setUser(user);
        material.setTitle(fileName);
        material.setType("text");
        material.setContent(content);
        material.setSummary(content.substring(0, Math.min(120, content.length())));
        material.setTagsJson(writeJson(List.of("上传")));
        material.setStatus("pending");
        material.setSource("UPLOAD");
        materialRepository.saveAndFlush(material);
        fingerprints.update(material);
        job.setResultMaterialId(material.getId());
        if(materialEvidence!=null)materialEvidence.capture(material);
        uploadJobRepository.save(job);
        return new FileImportJobDto(job.getId(), job.getFileName(), job.getStatus(), job.getProgress(), job.getMessage(), job.getResultMaterialId());
    }

    @Transactional(readOnly = true)
    public FileImportJobDto getUploadStatus(UUID jobId) {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails details))
            throw new org.springframework.security.access.AccessDeniedException("上传任务归属不可验证");
        User current = accessGuard.currentUser(details);
        MaterialUploadJob job = uploadJobRepository.findByIdAndOwnerUserId(jobId, current.getId())
                .orElseThrow(() -> new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "UPLOAD_JOB_NOT_FOUND"));
        return new FileImportJobDto(job.getId(), job.getFileName(), job.getStatus(), job.getProgress(), job.getMessage(), job.getResultMaterialId());
    }

    @Transactional(readOnly = true)
    public List<MaterialDto> pending() {
        return materialRepository.findByUserUsernameAndStatusIgnoreCase(accessGuard.currentUsername(), "pending")
                .stream().map(this::toDto).toList();
    }

    public MaterialDto review(UUID id, String action, MaterialReviewRequest request) {
        throw new org.springframework.security.access.AccessDeniedException("请通过本地管理员治理接口审核素材");
    }

    MaterialDto reviewInternal(UUID id, String action, MaterialReviewRequest request) {
        if (request == null) throw new com.ainovel.app.common.ApiStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "MATERIAL_REVIEW_REQUIRED");
        Material material = materialRepository.findByIdForUpdate(id).orElseThrow(() -> new BusinessException("素材不存在"));
        if (!Set.of("approve", "reject").contains(action)) throw new BusinessException("审核动作无效");
        if (request.title() != null) material.setTitle(request.title());
        if (request.summary() != null) material.setSummary(request.summary());
        if (request.tags() != null) material.setTagsJson(writeJson(request.tags()));
        if (request.type() != null) material.setType(request.type());
        material.setContentVersion(material.getContentVersion() + 1);
        material.setStatus("approve".equals(action) ? "approved" : "rejected");
        materialRepository.saveAndFlush(material);
        fingerprints.update(material);
        materialRetrievalService.indexMaterial(material.getUser(), material);
        return toDto(material);
    }

    public List<MaterialSearchResultDto> search(User user, MaterialSearchRequest request) {
        return materialRetrievalService.search(user, request);
    }

    public List<MaterialSearchResultDto> search(MaterialSearchRequest request) {
        return materialRetrievalService.search(null, request);
    }

    public List<MaterialSearchResultDto> autoHints(User user, AutoHintRequest request) {
        return search(user, new MaterialSearchRequest(request.text(), request.limit() != null ? request.limit() : 5));
    }

    public List<MaterialSearchResultDto> autoHints(AutoHintRequest request) {
        return autoHints(null, request);
    }

    public List<Map<String, Object>> findDuplicates() {
        throw new org.springframework.security.access.AccessDeniedException("请通过本地管理员后台启动查重任务");
    }

    @Transactional
    public MaterialDto merge(MaterialMergeRequest request) {
        return mergeInternal(request, false);
    }

    MaterialDto mergeInternal(MaterialMergeRequest request, boolean governance) {
        if (request == null || request.sourceMaterialId() == null || request.targetMaterialId() == null) {
            throw new BusinessException("源素材和目标素材不能为空");
        }
        if (request.sourceMaterialId().equals(request.targetMaterialId())) {
            throw new BusinessException("不能将素材合并到自身");
        }
        Material source = materialRepository.findByIdForUpdate(request.sourceMaterialId()).orElseThrow();
        Material target = materialRepository.findByIdForUpdate(request.targetMaterialId()).orElseThrow();
        if (!governance) { assertOwner(source.getUser()); assertOwner(target.getUser()); }
        if (Boolean.TRUE.equals(request.mergeTags())) {
            Set<String> tags = new LinkedHashSet<>();
            tags.addAll(readTags(target.getTagsJson()));
            tags.addAll(readTags(source.getTagsJson()));
            target.setTagsJson(writeJson(tags));
        }
        if (Boolean.TRUE.equals(request.mergeSummaryWhenEmpty()) && (target.getSummary() == null || target.getSummary().isBlank())) {
            target.setSummary(source.getSummary());
        }
        String targetContent = target.getContent() == null ? "" : target.getContent();
        String sourceContent = source.getContent() == null ? "" : source.getContent();
        if (targetContent.isBlank()) {
            target.setContent(sourceContent);
        } else if (!sourceContent.isBlank()) {
            target.setContent(targetContent + "\n\n" + sourceContent);
        }
        source.setStatus("rejected");
        source.setContentVersion(source.getContentVersion() + 1);
        target.setContentVersion(target.getContentVersion() + 1);
        if (!"MANUAL".equals(target.getSource()) && !"rejected".equals(target.getStatus())) target.setStatus("pending");
        materialRepository.saveAndFlush(source);
        materialRepository.saveAndFlush(target);
        fingerprints.update(source);
        fingerprints.update(target);
        materialRetrievalService.indexMaterial(source.getUser(), source);
        materialRetrievalService.indexMaterial(target.getUser(), target);
        return toDto(target);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> citations(UUID materialId) {
        return citationsInternal(materialId, false);
    }

    List<Map<String, Object>> citationsInternal(UUID materialId, boolean governance) {
        Material material = materialRepository.findById(materialId).orElseThrow(() -> new BusinessException("素材不存在"));
        if (!governance) assertOwner(material.getUser());
        List<String> signals = citationSignals(material);
        if (signals.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> citations = new ArrayList<>();
        for (Manuscript manuscript : manuscriptRepository.findByStoryUser(material.getUser())) {
            Map<String, String> sections = contents.readAll(manuscript);
            Map<String, SceneInfo> scenes = sceneInfoById(manuscript);
            for (Map.Entry<String, String> entry : sections.entrySet()) {
                String plain = stripHtml(entry.getValue());
                String matched = firstContainedSignal(plain, signals);
                if (matched == null) {
                    continue;
                }
                UUID sceneId = parseUuid(entry.getKey());
                SceneInfo scene = sceneId == null ? null : scenes.get(sceneId.toString());
                Map<String, Object> citation = new LinkedHashMap<>();
                citation.put("materialId", materialId);
                citation.put("storyId", manuscript.getOutline().getStory().getId());
                citation.put("storyTitle", safe(manuscript.getOutline().getStory().getTitle()));
                citation.put("manuscriptId", manuscript.getId());
                citation.put("sceneId", sceneId == null ? entry.getKey() : sceneId);
                citation.put("chapterTitle", scene == null ? "" : scene.chapterTitle());
                citation.put("sceneTitle", scene == null ? "" : scene.sceneTitle());
                citation.put("snippet", snippetAround(plain, matched));
                citation.put("reason", "signal:" + matched);
                citations.add(citation);
            }
        }
        return citations;
    }

    private List<String> citationSignals(Material material) {
        LinkedHashSet<String> signals = new LinkedHashSet<>();
        addSignal(signals, material.getTitle());
        for (String tag : readTags(material.getTagsJson())) {
            addSignal(signals, tag);
        }
        for (String token : tokens(safe(material.getSummary()) + " " + safe(material.getContent()))) {
            if (token.length() >= 4) {
                signals.add(token);
            }
        }
        return signals.stream().filter(signal -> signal.length() >= 3).limit(20).toList();
    }

    private void addSignal(Set<String> signals, String raw) {
        String value = safe(raw).trim();
        if (value.length() >= 3) {
            signals.add(value);
        }
    }

    private String firstContainedSignal(String text, List<String> signals) {
        for (String signal : signals) {
            if (text.contains(signal)) {
                return signal;
            }
        }
        return null;
    }

    private Map<String, SceneInfo> sceneInfoById(Manuscript manuscript) {
        Map<String, SceneInfo> result = new HashMap<>();
        String json = manuscript.getOutline() == null ? null : manuscript.getOutline().getContentJson();
        Map<String, Object> root = readObjectMap(json);
        for (Map<String, Object> chapter : listOfMap(root.get("chapters"))) {
            String chapterTitle = safe(String.valueOf(chapter.getOrDefault("title", "")));
            for (Map<String, Object> scene : listOfMap(chapter.get("scenes"))) {
                Object id = scene.get("id");
                if (id != null) {
                    result.put(String.valueOf(id), new SceneInfo(chapterTitle, safe(String.valueOf(scene.getOrDefault("title", "")))));
                }
            }
        }
        return result;
    }

    private String snippetAround(String text, String signal) {
        int index = text.indexOf(signal);
        if (index < 0) {
            return text.substring(0, Math.min(text.length(), 120));
        }
        int start = Math.max(0, index - 40);
        int end = Math.min(text.length(), index + signal.length() + 80);
        return text.substring(start, end);
    }

    private Set<String> tokens(String raw) {
        String normalized = safe(raw)
                .replaceAll("[\\p{Punct}\\s，。！？、；：“”‘’（）《》【】]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return Set.of();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : normalized.split("\\s+")) {
            String token = part.trim();
            if (token.length() >= 2) {
                out.add(token);
                if (containsCjk(token)) {
                    addCjkNgrams(out, token, 2);
                    addCjkNgrams(out, token, 3);
                }
            }
        }
        return out;
    }

    private boolean containsCjk(String token) {
        for (int i = 0; i < token.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(token.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private void addCjkNgrams(Set<String> out, String token, int size) {
        if (token.length() < size) {
            return;
        }
        for (int i = 0; i <= token.length() - size; i++) {
            out.add(token.substring(i, i + size));
        }
    }

    private double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(left);
        intersection.retainAll(right);
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    private String stripHtml(String text) {
        return HTML_TAG_PATTERN.matcher(safe(text)).replaceAll("");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, String> readStringMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    private Map<String, Object> readObjectMap(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new HashMap<>());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOfMap(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                out.add((Map<String, Object>) map);
            }
        }
        return out;
    }

    MaterialDto toDto(Material material) {
        return new MaterialDto(material.getId(), material.getTitle(), material.getType(), material.getSummary(), material.getContent(), readTags(material.getTagsJson()), material.getStatus(), material.getCreatedAt());
    }

    private void assertOwner(User owner) {
        if (owner == null || !Objects.equals(accessGuard.currentUsername(), owner.getUsername()))
            throw new org.springframework.security.access.AccessDeniedException("无权访问其他用户素材");
    }

    private List<String> readTags(String json) {
        return jsonColumnCodec.read(json, new TypeReference<>() {}, new ArrayList<>());
    }

    private String writeJson(Object obj) {
        return jsonColumnCodec.writeRequired(obj == null ? List.of() : obj);
    }

}

record DuplicateScore(double score, List<String> reasons) {}

record SceneInfo(String chapterTitle, String sceneTitle) {}
