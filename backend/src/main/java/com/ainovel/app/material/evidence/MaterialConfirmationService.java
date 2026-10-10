package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Author confirmations annotate source revisions. They never write the novel's H1 ledger. */
@Service
public class MaterialConfirmationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final JsonColumnCodec codec;
    private final MaterialEvidenceService sources;

    public MaterialConfirmationService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            JsonColumnCodec codec,
            MaterialEvidenceService sources) {
        this.jdbc = jdbc;
        this.json = json;
        this.codec = codec;
        this.sources = sources;
    }

    @Transactional
    public Annotation confirm(User user, String taskId, ConfirmCandidate request) {
        jdbc.queryForObject(
                "select id from users where id=? for update", byte[].class, bytes(user.getId()));
        String scope = "analysis:" + taskId;
        String hash = EvidenceText.hash(codec.writeRequired(request));
        var receipts =
                jdbc.queryForList(
                        "select request_hash,result_json from material_mutation_receipts where"
                                + " owner_id=? and scope=? and request_key=? for update",
                        bytes(user.getId()),
                        scope,
                        request.requestKey());
        if (!receipts.isEmpty()) {
            if (!hash.equals(receipts.getFirst().get("request_hash")))
                throw error(HttpStatus.CONFLICT, "MATERIAL_REQUEST_CONFLICT");
            return read((String) receipts.getFirst().get("result_json"), Annotation.class);
        }
        var tasks =
                jdbc.queryForList(
                        "select"
                            + " j.source_revision,j.result_json,r.material_id,r.content_version,r.content"
                            + " from material_processing_jobs j join material_revisions r on"
                            + " r.id=j.source_revision join materials m on m.id=r.material_id and"
                            + " m.content_version=r.content_version where j.id=? and j.owner_id=?"
                            + " and j.kind='EXTRACT' and j.status='COMPLETED' and m.user_id=? for"
                            + " update",
                        taskId,
                        bytes(user.getId()),
                        bytes(user.getId()));
        if (tasks.isEmpty()) throw error(HttpStatus.CONFLICT, "MATERIAL_ANALYSIS_NOT_CURRENT");
        var task = tasks.getFirst();
        long version = ((Number) task.get("content_version")).longValue();
        if (version != request.expectedVersion())
            throw error(HttpStatus.CONFLICT, "SOURCE_VERSION_CONFLICT");
        String revision = (String) task.get("source_revision");
        JsonNode analysis = read((String) task.get("result_json"), JsonNode.class).path("analysis");
        EvidenceReportValidator.validateAnalysis(
                analysis, Map.of("source:" + revision, (String) task.get("content")));
        JsonNode candidate = analysis.path("candidates").path(request.candidateIndex());
        if (candidate.isMissingNode())
            throw error(HttpStatus.BAD_REQUEST, "MATERIAL_CANDIDATE_NOT_FOUND");
        var existing =
                jdbc.queryForList(
                        "select id,annotation_kind,name,candidate_json from"
                            + " material_confirmed_annotations where owner_id=? and task_id=? and"
                            + " candidate_index=? for update",
                        bytes(user.getId()),
                        taskId,
                        request.candidateIndex());
        Annotation result;
        if (!existing.isEmpty()) {
            var row = existing.getFirst();
            result =
                    new Annotation(
                            (String) row.get("id"),
                            revision,
                            (String) row.get("annotation_kind"),
                            (String) row.get("name"),
                            read((String) row.get("candidate_json"), JsonNode.class));
        } else {
            String kind = candidate.path("type").asText();
            String name = candidate.path("name").asText();
            if (name.isBlank() || name.length() > 255)
                throw error(HttpStatus.BAD_REQUEST, "MATERIAL_CANDIDATE_NAME_INVALID");
            if (kind.equals("ENTITY")) {
                List<String> aliases = new ArrayList<>();
                candidate.path("aliases").forEach(value -> aliases.add(value.asText()));
                sources.createEntity(
                        user,
                        new EntityWrite(
                                name,
                                aliases,
                                Map.of(uuid((byte[]) task.get("material_id")), version),
                                "analysis:" + taskId + ":" + request.candidateIndex()));
            }
            result = new Annotation(UUID.randomUUID().toString(), revision, kind, name, candidate);
            jdbc.update(
                    "insert into"
                        + " material_confirmed_annotations(id,owner_id,revision_id,task_id,candidate_index,annotation_kind,name,candidate_json)"
                        + " values(?,?,?,?,?,?,?,?)",
                    result.id(),
                    bytes(user.getId()),
                    revision,
                    taskId,
                    request.candidateIndex(),
                    kind,
                    name,
                    codec.writeRequired(candidate));
        }
        jdbc.update(
                "insert into"
                    + " material_mutation_receipts(owner_id,scope,request_key,request_hash,result_json)"
                    + " values(?,?,?,?,?)",
                bytes(user.getId()),
                scope,
                request.requestKey(),
                hash,
                codec.writeRequired(result));
        jdbc.update(
                "update material_hint_slots set cache_key=null,result_json=null where owner_id=?",
                bytes(user.getId()));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Annotation> list(User user, String revision) {
        if (jdbc.queryForObject(
                        "select count(*) from material_revisions r join materials m on"
                            + " m.id=r.material_id and m.content_version=r.content_version where"
                            + " r.id=? and m.user_id=?",
                        Integer.class,
                        revision,
                        bytes(user.getId()))
                != 1) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
        return jdbc.query(
                "select id,annotation_kind,name,candidate_json from material_confirmed_annotations"
                        + " where owner_id=? and revision_id=? order by created_at,id",
                (row, n) ->
                        new Annotation(
                                row.getString(1),
                                revision,
                                row.getString(2),
                                row.getString(3),
                                read(row.getString(4), JsonNode.class)),
                bytes(user.getId()),
                revision);
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception invalid) {
            throw new IllegalStateException("STORED_MATERIAL_ANALYSIS_INVALID");
        }
    }

    private static com.ainovel.app.common.ApiStatusException error(HttpStatus status, String code) {
        return MaterialEvidenceService.error(status, code);
    }
}
