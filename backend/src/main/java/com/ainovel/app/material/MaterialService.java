package com.ainovel.app.material;

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

@Service
public class MaterialService {
    @Autowired
    private MaterialRepository materialRepository;
    @Autowired
    private MaterialUploadJobRepository uploadJobRepository;
    @Autowired
    private ResourceAccessGuard accessGuard;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        return toDto(material);
    }

    public List<MaterialSearchResultDto> search(MaterialSearchRequest request) {
        String q = request.query() == null ? "" : request.query().toLowerCase();
        int limit = request.limit() != null ? request.limit() : 10;
        boolean admin = accessGuard.isCurrentUserAdmin();
        String username = admin ? "" : accessGuard.currentUsername();
        return materialRepository.findAll().stream()
                .filter(m -> admin || (m.getUser() != null && username.equals(m.getUser().getUsername())))
                .filter(m -> m.getTitle().toLowerCase().contains(q) || (m.getSummary() != null && m.getSummary().toLowerCase().contains(q)) || (m.getContent() != null && m.getContent().toLowerCase().contains(q)))
                .limit(limit)
                .map(m -> new MaterialSearchResultDto(m.getId(), m.getTitle(), snippet(m.getContent()), Math.random(), 0))
                .toList();
    }

    public List<MaterialSearchResultDto> autoHints(AutoHintRequest request) {
        return search(new MaterialSearchRequest(request.text(), request.limit() != null ? request.limit() : 5));
    }

    public List<Map<String, Object>> findDuplicates() {
        accessGuard.assertAdmin();
        return List.of();
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
        return List.of();
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

    private String snippet(String content) {
        if (content == null) return "";
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }
}
