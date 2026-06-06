package com.ainovel.app.material;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.dto.*;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.model.MaterialUploadJob;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.material.repo.MaterialUploadJobRepository;
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
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private MaterialUploadJobRepository uploadJobRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    @Autowired
    private MaterialRetrievalService materialRetrievalService;
    @Autowired
    private ManuscriptRepository manuscriptRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    public MaterialDto create(User user, MaterialCreateRequest request) {
        Material material = new Material();
        material.setUser(user);
        material.setTitle(request.title());
        material.setType(request.type());
        material.setSummary(request.summary());
        material.setContent(request.content());
        material.setTagsJson(writeJson(request.tags()));
        material.setStatus("approved");
        materialRepository.save(material);
        materialRetrievalService.indexMaterial(user, material);
        return toDto(material);
    }

    public List<MaterialDto> list(User user) {
        accessGuard.assertCurrentUserEquals(user.getUsername());
        return materialRepository.findByUser(user).stream().map(this::toDto).toList();
    }

    public MaterialDto get(UUID id) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new RuntimeException("素材不存在"));
        accessGuard.assertOwner(material.getUser());
        return toDto(material);
    }

    @Transactional
    public MaterialDto update(UUID id, MaterialUpdateRequest request) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new RuntimeException("素材不存在"));
        accessGuard.assertOwner(material.getUser());
        if (request.title() != null) material.setTitle(request.title());
        if (request.type() != null) material.setType(request.type());
        if (request.summary() != null) material.setSummary(request.summary());
        if (request.content() != null) material.setContent(request.content());
        if (request.tags() != null) material.setTagsJson(writeJson(request.tags()));
        if (request.status() != null) material.setStatus(request.status());
        if (request.entitiesJson() != null) material.setEntitiesJson(request.entitiesJson());
        materialRepository.save(material);
        materialRetrievalService.indexMaterial(material.getUser(), material);
        return toDto(material);
    }

    public void delete(UUID id) {
        Material material = materialRepository.findById(id).orElseThrow(() -> new RuntimeException("素材不存在"));
        accessGuard.assertOwner(material.getUser());
        materialRepository.delete(material);
    }

    public FileImportJobDto createUploadJob(User user, String fileName, String content) {
        MaterialUploadJob job = new MaterialUploadJob();
        job.setFileName(fileName);
        job.setStatus("processing");
        job.setProgress(0);
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
        materialRepository.save(material);
        job.setResultMaterialId(material.getId());
        uploadJobRepository.save(job);
        return new FileImportJobDto(job.getId(), job.getFileName(), job.getStatus(), job.getProgress(), job.getMessage());
    }

    public FileImportJobDto getUploadStatus(UUID jobId) {
        MaterialUploadJob job = uploadJobRepository.findById(jobId).orElseThrow(() -> new RuntimeException("上传任务不存在"));
        if (job.getResultMaterialId() != null) {
            Material material = materialRepository.findById(job.getResultMaterialId()).orElse(null);
            if (material != null) {
                accessGuard.assertOwner(material.getUser());
            }
        }
        if (job.getProgress() < 100) {
            job.setProgress(100);
            job.setStatus("completed");
            uploadJobRepository.save(job);
        }
        return new FileImportJobDto(job.getId(), job.getFileName(), job.getStatus(), job.getProgress(), job.getMessage());
    }

    public List<MaterialDto> pending() {
        if (accessGuard.isCurrentUserAdmin()) {
            return materialRepository.findAll().stream().filter(m -> "pending".equalsIgnoreCase(m.getStatus())).map(this::toDto).toList();
        }
        String username = accessGuard.currentUsername();
        return materialRepository.findAll().stream()
                .filter(m -> m.getUser() != null && username.equals(m.getUser().getUsername()))
                .filter(m -> "pending".equalsIgnoreCase(m.getStatus()))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public MaterialDto review(UUID id, String action, MaterialReviewRequest request) {
        accessGuard.assertAdmin();
        Material material = materialRepository.findById(id).orElseThrow(() -> new RuntimeException("素材不存在"));
        if (request.title() != null) material.setTitle(request.title());
        if (request.summary() != null) material.setSummary(request.summary());
        if (request.tags() != null) material.setTagsJson(writeJson(request.tags()));
        if (request.type() != null) material.setType(request.type());
        material.setStatus("approve".equalsIgnoreCase(action) ? "approved" : "rejected");
        materialRepository.save(material);
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
        accessGuard.assertAdmin();
        List<Material> materials = materialRepository.findAll().stream()
                .filter(material -> !"rejected".equalsIgnoreCase(material.getStatus()))
                .toList();
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            for (int j = i + 1; j < materials.size(); j++) {
                Material left = materials.get(i);
                Material right = materials.get(j);
                DuplicateScore score = duplicateScore(left, right);
                if (score.score() >= 0.5) {
                    Map<String, Object> candidate = new LinkedHashMap<>();
                    candidate.put("sourceMaterialId", left.getId());
                    candidate.put("targetMaterialId", right.getId());
                    candidate.put("sourceTitle", safe(left.getTitle()));
                    candidate.put("targetTitle", safe(right.getTitle()));
                    candidate.put("score", score.score());
                    candidate.put("reasons", score.reasons());
                    candidates.add(candidate);
                }
            }
        }
        candidates.sort((a, b) -> Double.compare(((Number) b.get("score")).doubleValue(), ((Number) a.get("score")).doubleValue()));
        return candidates;
    }

    @Transactional
    public MaterialDto merge(MaterialMergeRequest request) {
        Material source = materialRepository.findById(request.sourceMaterialId()).orElseThrow();
        Material target = materialRepository.findById(request.targetMaterialId()).orElseThrow();
        if (!accessGuard.isCurrentUserAdmin()) {
            accessGuard.assertOwner(source.getUser());
            accessGuard.assertOwner(target.getUser());
        }
        if (Boolean.TRUE.equals(request.mergeTags())) {
            Set<String> tags = new LinkedHashSet<>();
            tags.addAll(readTags(target.getTagsJson()));
            tags.addAll(readTags(source.getTagsJson()));
            target.setTagsJson(writeJson(tags));
        }
        if (Boolean.TRUE.equals(request.mergeSummaryWhenEmpty()) && (target.getSummary() == null || target.getSummary().isBlank())) {
            target.setSummary(source.getSummary());
        }
        target.setContent(target.getContent() + "\n\n" + source.getContent());
        materialRepository.save(target);
        return toDto(target);
    }

    public List<Map<String, Object>> citations(UUID materialId) {
        Material material = materialRepository.findById(materialId).orElseThrow(() -> new RuntimeException("素材不存在"));
        accessGuard.assertOwner(material.getUser());
        List<String> signals = citationSignals(material);
        if (signals.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> citations = new ArrayList<>();
        for (Manuscript manuscript : manuscriptRepository.findByStoryUser(material.getUser())) {
            Map<String, String> sections = readStringMap(manuscript.getSectionsJson());
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

    private DuplicateScore duplicateScore(Material left, Material right) {
        double score = 0.0;
        List<String> reasons = new ArrayList<>();
        double title = jaccard(tokens(left.getTitle()), tokens(right.getTitle()));
        if (title >= 0.3) {
            score += 0.35;
            reasons.add("title");
        }
        double tags = jaccard(new LinkedHashSet<>(readTags(left.getTagsJson())), new LinkedHashSet<>(readTags(right.getTagsJson())));
        if (tags >= 0.3) {
            score += 0.3;
            reasons.add("tags");
        }
        double text = jaccard(tokens(safe(left.getSummary()) + " " + safe(left.getContent())),
                tokens(safe(right.getSummary()) + " " + safe(right.getContent())));
        if (text >= 0.2) {
            score += 0.25;
            reasons.add("content");
        }
        return new DuplicateScore(Math.min(1.0, score), reasons);
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
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
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

    private MaterialDto toDto(Material material) {
        return new MaterialDto(material.getId(), material.getTitle(), material.getType(), material.getSummary(), material.getContent(), readTags(material.getTagsJson()), material.getStatus(), material.getCreatedAt());
    }

    private List<String> readTags(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private String writeJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj);} catch (Exception e) { return "[]"; }
    }

}

record DuplicateScore(double score, List<String> reasons) {}

record SceneInfo(String chapterTitle, String sceneTitle) {}
