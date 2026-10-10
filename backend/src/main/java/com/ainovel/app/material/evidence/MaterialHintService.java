package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

/** A scene gets one automatic lookup per 30 seconds; cache hits are permission checked again. */
@Service
public class MaterialHintService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ResourceAccessGuard access;
    private final MaterialEvidenceService sources;
    private final MaterialSearchService search;
    private final JsonColumnCodec codec;
    private final ObjectMapper json;

    private record Claim(UUID story, String key, String cached, boolean granted) {}

    public MaterialHintService(
            JdbcTemplate jdbc,
            TransactionTemplate transactions,
            ResourceAccessGuard access,
            MaterialEvidenceService sources,
            MaterialSearchService search,
            JsonColumnCodec codec,
            ObjectMapper json) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactions.getTransactionManager());
        this.access = access;
        this.sources = sources;
        this.search = search;
        this.codec = codec;
        this.json = json;
    }

    public Results hints(User user, Hint request) {
        Claim claim = transactions.execute(tx -> claim(user, request));
        if (claim == null) return new Results("fact", "bound", List.of(), List.of("自动提示已关闭"));
        Search query = new Search(request.query(), claim.story(), "fact", "bound", 8);
        if (claim.cached() != null) {
            try {
                return recheck(user, query, json.readValue(claim.cached(), Results.class));
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                throw new IllegalStateException("HINT_CACHE_INVALID", invalid);
            }
        }
        if (!claim.granted())
            return new Results("fact", "bound", List.of(), List.of("同一场景的自动提示每 30 秒最多一次"));
        Results result = search.search(user, query);
        transactions.executeWithoutResult(
                tx ->
                        jdbc.update(
                                "update material_hint_slots set result_json=? where owner_id=? and"
                                        + " manuscript_id=? and scene_id=? and cache_key=?",
                                codec.writeRequired(result),
                                bytes(user.getId()),
                                bytes(request.manuscriptId()),
                                request.sceneId().toString(),
                                claim.key()));
        return recheck(user, query, result);
    }

    private Claim claim(User user, Hint request) {
        var manuscript = access.requireOwnedManuscript(request.manuscriptId(), user);
        UUID story = manuscript.getOutline().getStory().getId();
        // Validate the scene belongs to this outline, rather than accepting arbitrary throttle
        // keys.
        var outline =
                codec.readRequired(
                        manuscript.getOutline().getContentJson(),
                        new com.fasterxml.jackson.core.type.TypeReference<
                                com.fasterxml.jackson.databind.JsonNode>() {});
        boolean found = false;
        for (var chapter : outline.path("chapters"))
            for (var scene : chapter.path("scenes")) {
                if (request.sceneId().toString().equals(scene.path("id").asText())) found = true;
            }
        if (!found)
            throw MaterialEvidenceService.error(
                    org.springframework.http.HttpStatus.NOT_FOUND, "SCENE_NOT_FOUND");
        Settings settings = sources.settings(user, story);
        if (!settings.hints()) return null;
        var revisions =
                jdbc.queryForList(
                        "select hex(b.material_id) as id,m.content_version,m.status,hex(m.user_id)"
                                + " as owner from material_work_bindings b left join materials m on"
                                + " m.id=b.material_id where b.story_id=? order by b.material_id",
                        bytes(story));
        String key =
                EvidenceText.hash(
                        codec.writeRequired(
                                List.of(
                                        user.getId(),
                                        request.query(),
                                        settings,
                                        revisions,
                                        "retrieval-cp900-120-rrf60-v1")));
        jdbc.update(
                "insert ignore into material_hint_slots(owner_id,manuscript_id,scene_id)"
                        + " values(?,?,?)",
                bytes(user.getId()),
                bytes(request.manuscriptId()),
                request.sceneId().toString());
        var slot =
                jdbc.queryForMap(
                        "select cache_key,result_json,observed_at >="
                            + " DATE_SUB(CURRENT_TIMESTAMP(6),interval 30 second) as recent from"
                            + " material_hint_slots where owner_id=? and manuscript_id=? and"
                            + " scene_id=? for update",
                        bytes(user.getId()),
                        bytes(request.manuscriptId()),
                        request.sceneId().toString());
        if (key.equals(slot.get("cache_key")) && slot.get("result_json") != null)
            return new Claim(story, key, (String) slot.get("result_json"), false);
        Object recent = slot.get("recent");
        if (Boolean.TRUE.equals(recent) || recent instanceof Number value && value.intValue() == 1)
            return new Claim(story, key, null, false);
        jdbc.update(
                "update material_hint_slots set"
                        + " cache_key=?,result_json=null,observed_at=CURRENT_TIMESTAMP(6) where"
                        + " owner_id=? and manuscript_id=? and scene_id=?",
                key,
                bytes(user.getId()),
                bytes(request.manuscriptId()),
                request.sceneId().toString());
        return new Claim(story, key, null, true);
    }

    private Results recheck(User user, Search query, Results result) {
        var ids =
                sources
                        .visibleHits(
                                user, query, result.items().stream().map(Hit::chunkId).toList())
                        .stream()
                        .map(Hit::chunkId)
                        .collect(java.util.stream.Collectors.toSet());
        return new Results(
                result.mode(),
                result.scope(),
                result.items().stream().filter(hit -> ids.contains(hit.chunkId())).toList(),
                result.degradation());
    }
}
