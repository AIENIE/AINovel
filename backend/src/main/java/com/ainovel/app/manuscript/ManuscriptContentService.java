package com.ainovel.app.manuscript;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.model.Manuscript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;

/** The sole live working-copy access boundary. Historical snapshots never pass through this writer. */
@Service
public class ManuscriptContentService {
    private final JdbcTemplate jdbc;
    private final JsonColumnCodec json;
    private final boolean scenesEnabled;
    public ManuscriptContentService(JdbcTemplate jdbc, JsonColumnCodec json,
                                    @Value("${app.manuscript.scene-storage.enabled:false}") boolean scenesEnabled) {
        this.jdbc = jdbc; this.json = json; this.scenesEnabled = scenesEnabled;
    }
    public boolean usesScenes() { return scenesEnabled; }
    private boolean normalized(Manuscript m) {
        if (scenesEnabled && m.getContentStorageVersion() != 2)
            throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MANUSCRIPT_MIGRATION_REQUIRED");
        if (!scenesEnabled && m.getContentStorageVersion() == 2)
            throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MANUSCRIPT_STORAGE_DOWNGRADE_BLOCKED");
        return scenesEnabled;
    }
    @Transactional(readOnly = true)
    public Map<String,String> readAll(Manuscript m) {
        if (!normalized(m)) return json.readSections(m.getSectionsJson());
        Map<String,String> result = new LinkedHashMap<>();
        jdbc.query("select scene_id,content from manuscript_scene_contents where manuscript_id=? order by scene_id",
                r -> { result.put(uuid(r.getBytes("scene_id")).toString(), r.getString("content")); }, bytes(m.getId()));
        return result;
    }
    @Transactional(readOnly = true)
    public String readScene(Manuscript m, UUID sceneId) {
        if (!normalized(m)) return json.readSections(m.getSectionsJson()).getOrDefault(sceneId.toString(), "");
        return jdbc.query("select content from manuscript_scene_contents where manuscript_id=? and scene_id=?",
                (r,n) -> r.getString(1), bytes(m.getId()), bytes(sceneId)).stream().findFirst().orElse("");
    }
    public String snapshot(Manuscript m) { return json.writeRequired(readAll(m)); }
    public void initialize(Manuscript m) {
        m.setSectionsJson("{}"); m.setContentStorageVersion(scenesEnabled ? 2 : 1);
    }
    @Transactional
    public void writeScene(Manuscript m, UUID sceneId, String content) {
        Objects.requireNonNull(content, "content");
        invalidateEvidence(m);
        if (!normalized(m)) {
            Map<String,String> values = json.readSections(m.getSectionsJson()); values.put(sceneId.toString(), content);
            m.setSectionsJson(json.writeRequired(values));
        } else {
            int changed = jdbc.update("update manuscript_scene_contents set content=?,word_count=?,updated_at=CURRENT_TIMESTAMP where manuscript_id=? and scene_id=?",
                    content, words(content), bytes(m.getId()), bytes(sceneId));
            if (changed == 0) jdbc.update("insert into manuscript_scene_contents(manuscript_id,scene_id,content,word_count,updated_at) values(?,?,?,?,CURRENT_TIMESTAMP)", bytes(m.getId()), bytes(sceneId), content, words(content));
        }
        // Dirty the parent so Hibernate's optimistic version arbitrates all scene/branch writes.
        m.setUpdatedAt(Instant.now());
    }
    @Transactional
    public void replaceAll(Manuscript m, Map<String,String> sections) {
        invalidateEvidence(m);
        Map<String,String> checked = json.readSections(json.writeRequired(sections));
        if (!normalized(m)) m.setSectionsJson(json.writeRequired(checked));
        else {
            jdbc.update("delete from manuscript_scene_contents where manuscript_id=?", bytes(m.getId()));
            for (var entry : checked.entrySet()) {
                UUID sceneId = strictId(entry.getKey());
                jdbc.update("insert into manuscript_scene_contents(manuscript_id,scene_id,content,word_count,updated_at) values(?,?,?,?,CURRENT_TIMESTAMP)", bytes(m.getId()), bytes(sceneId), entry.getValue(), words(entry.getValue()));
            }
        }
        m.setUpdatedAt(Instant.now());
    }
    public void replaceSnapshot(Manuscript m, String snapshot) { replaceAll(m, json.readSections(snapshot)); }
    private void invalidateEvidence(Manuscript m){
        jdbc.update("update material_source_links set state='REVIEW_REQUIRED' where manuscript_id=? and state='CURRENT'",bytes(m.getId()));
        jdbc.update("update material_processing_jobs set status='STALE' where manuscript_id=? and kind in ('CHECK','AUTO_CHECK') and status='COMPLETED'",bytes(m.getId()));
    }
    public static UUID strictId(String key) {
        try { UUID id = UUID.fromString(key); if (!id.toString().equalsIgnoreCase(key)) throw new IllegalArgumentException(); return id; }
        catch (RuntimeException ex) { throw new ApiStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_SCENE_ID_INVALID"); }
    }
    @Transactional(readOnly = true)
    public long wordCount(Manuscript m) {
        if (!normalized(m)) return readAll(m).values().stream().mapToLong(ManuscriptContentService::words).sum();
        Long count = jdbc.queryForObject("select coalesce(sum(word_count),0) from manuscript_scene_contents where manuscript_id=?", Long.class, bytes(m.getId()));
        return count == null ? 0 : count;
    }
    public static long words(String html) {
        String plain = org.springframework.web.util.HtmlUtils.htmlUnescape(html.replaceAll("<[^>]*>", ""));
        return plain.replaceAll("[\\s\\u00A0]+", "").length();
    }
}
