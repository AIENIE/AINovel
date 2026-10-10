package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;

import com.ainovel.app.material.MaterialService;
import com.ainovel.app.material.dto.*;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class MaterialMutationService {
    public record Create(
            @Valid @NotNull MaterialCreateRequest input,
            @NotBlank @Size(max = 80) String requestKey) {}

    public record Update(
            @Valid @NotNull MaterialUpdateRequest input,
            @Min(1) long expectedVersion,
            @NotBlank @Size(max = 80) String requestKey) {}

    private record UploadIntent(String fileName, String content) {}

    public record Merge(
            @NotNull UUID first,
            @NotNull UUID second,
            @Min(1) long firstVersion,
            @Min(1) long secondVersion,
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 400000) String content,
            @NotBlank @Size(max = 80) String requestKey) {}

    private final MaterialService legacy;
    private final MaterialRepository materials;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MaterialMutationService(
            MaterialService legacy,
            MaterialRepository materials,
            JdbcTemplate jdbc,
            ObjectMapper json) {
        this.legacy = legacy;
        this.materials = materials;
        this.jdbc = jdbc;
        this.json = json;
    }

    private void lock(User user) {
        jdbc.queryForObject(
                "select id from users where id=? for update", byte[].class, bytes(user.getId()));
    }

    @Transactional
    public FileImportJobDto upload(User user, String fileName, String content, String requestKey) {
        if (fileName == null
                || fileName.length() > 255
                || !fileName.toLowerCase(Locale.ROOT).endsWith(".txt")
                || requestKey == null
                || requestKey.isBlank()
                || requestKey.length() > 80)
            throw MaterialEvidenceService.error(HttpStatus.BAD_REQUEST, "MATERIAL_UPLOAD_INVALID");
        if (content == null || content.isBlank())
            throw MaterialEvidenceService.error(HttpStatus.BAD_REQUEST, "MATERIAL_UPLOAD_EMPTY");
        lock(user);
        String scope = "material:upload";
        var input = new UploadIntent(fileName, content);
        var old = replay(user, scope, requestKey, input, FileImportJobDto.class);
        if (old != null) {
            if (old.materialId() == null || !materials.existsById(old.materialId()))
                throw MaterialEvidenceService.error(HttpStatus.GONE, "MATERIAL_RESULT_DELETED");
            return old;
        }
        EvidenceText.split(content);
        var result = legacy.createUploadJob(user, fileName, content);
        remember(user, scope, requestKey, input, result);
        return result;
    }

    @Transactional
    public MaterialDto create(User user, Create request) {
        lock(user);
        String scope = "material:create";
        var replay = replay(user, scope, request.requestKey(), request);
        if (replay != null) return replay;
        EvidenceText.split(request.input().content());
        var result = legacy.create(user, request.input());
        remember(user, scope, request.requestKey(), request, result);
        return result;
    }

    @Transactional
    public MaterialDto update(User user, UUID id, Update request) {
        lock(user);
        String scope = "material:update:" + id;
        var replay = replay(user, scope, request.requestKey(), request);
        if (replay != null) return replay;
        var material = owned(user, id, request.expectedVersion());
        if (request.input().content() != null) EvidenceText.split(request.input().content());
        var result = legacy.update(material.getId(), request.input());
        remember(user, scope, request.requestKey(), request, result);
        return result;
    }

    @Transactional
    public MaterialDto merge(User user, Merge request) {
        lock(user);
        String scope = "material:merge";
        var replay = replay(user, scope, request.requestKey(), request);
        if (replay != null) return replay;
        if (request.first().equals(request.second()))
            throw MaterialEvidenceService.error(
                    HttpStatus.BAD_REQUEST, "MATERIAL_MERGE_SAME_SOURCE");
        // Canonical order prevents two merge workers from locking the source pair in opposite
        // order.
        var ids = List.of(request.first(), request.second()).stream().sorted().toList();
        Map<UUID, Material> selected = new HashMap<>();
        for (UUID id : ids)
            selected.put(
                    id,
                    owned(
                            user,
                            id,
                            id.equals(request.first())
                                    ? request.firstVersion()
                                    : request.secondVersion()));
        EvidenceText.split(request.content());
        var result =
                legacy.create(
                        user,
                        new MaterialCreateRequest(
                                request.title(),
                                "text",
                                "作者确认合并的新资料版本",
                                request.content(),
                                List.of("合并")));
        String revision =
                jdbc.queryForObject(
                        "select id from material_revisions where material_id=? and"
                                + " content_version=1",
                        String.class,
                        bytes(result.id()));
        for (Material material : selected.values()) {
            String source =
                    jdbc.queryForObject(
                            "select id from material_revisions where material_id=? and"
                                    + " content_version=?",
                            String.class,
                            bytes(material.getId()),
                            material.getContentVersion());
            jdbc.update(
                    "insert into material_merge_sources(merged_revision,source_revision)"
                            + " values(?,?)",
                    revision,
                    source);
        }
        remember(user, scope, request.requestKey(), request, result);
        return result;
    }

    private Material owned(User user, UUID id, long version) {
        var m =
                materials
                        .findByIdForUpdate(id)
                        .orElseThrow(
                                () ->
                                        MaterialEvidenceService.error(
                                                HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE"));
        if (m.getUser() == null || !m.getUser().getId().equals(user.getId()))
            throw MaterialEvidenceService.error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
        if (m.getContentVersion() != version)
            throw MaterialEvidenceService.error(HttpStatus.CONFLICT, "SOURCE_VERSION_CONFLICT");
        return m;
    }

    private MaterialDto replay(User user, String scope, String key, Object input) {
        var result = replay(user, scope, key, input, MaterialDto.class);
        if (result != null && !materials.existsById(result.id()))
            throw MaterialEvidenceService.error(HttpStatus.GONE, "MATERIAL_RESULT_DELETED");
        return result;
    }

    private <T> T replay(User user, String scope, String key, Object input, Class<T> type) {
        var rows =
                jdbc.queryForList(
                        "select request_hash,result_json from material_mutation_receipts where"
                                + " owner_id=? and scope=? and request_key=? for update",
                        bytes(user.getId()),
                        scope,
                        key);
        if (rows.isEmpty()) return null;
        if (!EvidenceText.hash(encode(input)).equals(rows.getFirst().get("request_hash")))
            throw MaterialEvidenceService.error(HttpStatus.CONFLICT, "MATERIAL_REQUEST_CONFLICT");
        try {
            return json.readValue((String) rows.getFirst().get("result_json"), type);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
            throw new IllegalStateException("STORED_MATERIAL_RECEIPT_INVALID");
        }
    }

    private void remember(User user, String scope, String key, Object input, Object result) {
        jdbc.update(
                "insert into"
                    + " material_mutation_receipts(owner_id,scope,request_key,request_hash,result_json)"
                    + " values(?,?,?,?,?)",
                bytes(user.getId()),
                scope,
                key,
                EvidenceText.hash(encode(input)),
                encode(result));
    }

    private String encode(Object input) {
        try {
            return json.writeValueAsString(input);
        } catch (Exception invalid) {
            throw new IllegalStateException("MATERIAL_REQUEST_INVALID");
        }
    }
}
