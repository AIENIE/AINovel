package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.material.evidence.EvidenceDtos.Package;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Immutable sources and permission checks shared by search, references and verification. */
@Service
public class MaterialEvidenceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ResourceAccessGuard access;
    private final com.ainovel.app.manuscript.ManuscriptContentService contents;

    public MaterialEvidenceService(
            JdbcTemplate jdbc,
            ObjectMapper json,
            ResourceAccessGuard access,
            com.ainovel.app.manuscript.ManuscriptContentService contents) {
        this.jdbc = jdbc;
        this.json = json;
        this.access = access;
        this.contents = contents;
    }

    @Transactional
    public void capture(Material material) {
        String content = Objects.requireNonNullElse(material.getContent(), "");
        if (content.codePointCount(0, content.length()) > 400000)
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "MATERIAL_TEXT_LIMIT");
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "insert ignore into"
                    + " material_revisions(id,material_id,content_version,owner_id,title,content,tags_json,content_hash)"
                    + " values(?,?,?,?,?,?,?,?)",
                id,
                bytes(material.getId()),
                material.getContentVersion(),
                material.getUser() == null ? null : bytes(material.getUser().getId()),
                Objects.requireNonNullElse(material.getTitle(), ""),
                content,
                Objects.requireNonNullElse(material.getTagsJson(), "[]"),
                EvidenceText.hash(content));
        id =
                jdbc.queryForObject(
                        "select id from material_revisions where material_id=? and"
                                + " content_version=?",
                        String.class,
                        bytes(material.getId()),
                        material.getContentVersion());
        jdbc.update("insert ignore into material_basic_jobs(revision_id) values(?)", id);
        jdbc.update(
                "update material_source_links l join material_revisions r on r.id=l.revision_id set"
                    + " l.state='REVIEW_REQUIRED' where r.material_id=? and r.content_version<>?",
                bytes(material.getId()),
                material.getContentVersion());
    }

    private void project(String revision, UUID material, String title, String content) {
        List<Object[]> batch = new ArrayList<>();
        for (var fragment : EvidenceText.split(content)) {
            String id =
                    UUID.nameUUIDFromBytes(
                                    (revision + ":" + fragment.seq() + ":cp900-120-v1")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toString();
            batch.add(
                    new Object[] {
                        id,
                        revision,
                        bytes(material),
                        fragment.seq(),
                        fragment.start(),
                        fragment.end(),
                        title,
                        fragment.text()
                    });
        }
        if (!batch.isEmpty())
            jdbc.batchUpdate(
                    "insert ignore into"
                        + " material_evidence_chunks(id,revision_id,material_id,seq,start_cp,end_cp,title,text)"
                        + " values(?,?,?,?,?,?,?,?)",
                    batch);
    }

    @Transactional
    public void backfill(int limit) {
        var rows =
                jdbc.queryForList(
                        "select r.* from material_basic_jobs j join material_revisions r on"
                            + " r.id=j.revision_id where j.status='QUEUED' order by r.created_at"
                            + " limit ? for update skip locked",
                        Math.min(2, Math.max(1, limit)));
        for (var row : rows) {
            String revision = (String) row.get("id");
            project(
                    revision,
                    uuid((byte[]) row.get("material_id")),
                    (String) row.get("title"),
                    (String) row.get("content"));
            jdbc.update(
                    "update material_basic_jobs set"
                            + " status='COMPLETED',completed_at=CURRENT_TIMESTAMP(6) where"
                            + " revision_id=?",
                    revision);
        }
    }

    private String visibility(String scope) {
        return switch (scope) {
            case "bound" ->
                    "and exists(select 1 from material_work_bindings b where b.story_id=? and"
                            + " b.material_id=m.id) and (m.user_id=? or m.user_id is null) ";
            case "personal" -> "and m.user_id=? ";
            case "public" -> "and m.user_id is null ";
            default -> throw error(HttpStatus.BAD_REQUEST, "MATERIAL_SCOPE_INVALID");
        };
    }

    private List<Object> visibilityArgs(User user, UUID story, String scope) {
        return new ArrayList<>(
                switch (scope) {
                    case "bound" -> List.of(bytes(story), bytes(user.getId()));
                    case "personal" -> List.of(bytes(user.getId()));
                    case "public" -> List.of();
                    default -> throw error(HttpStatus.BAD_REQUEST, "MATERIAL_SCOPE_INVALID");
                });
    }

    private static final String CURRENT =
            " from material_evidence_chunks c join material_revisions r on r.id=c.revision_id join"
                + " materials m on m.id=r.material_id and m.content_version=r.content_version where"
                + " m.status='approved' ";

    @Transactional(readOnly = true)
    public Results search(User user, Search query) {
        var recall = basicRecall(user, query);
        int limit = query.limit() == null ? 8 : Math.min(40, Math.max(1, query.limit()));
        var degraded = new ArrayList<String>();
        var settings = settings(user, query.storyId());
        if (!settings.semanticProfile().equals("basic")) degraded.add("语义检索尚未通过选型验收，使用基础检索");
        if (settings.rerank()) degraded.add("重排尚未通过选型验收");
        return new Results(
                recall.mode(),
                recall.scope(),
                fuse(recall.rankings()).stream().limit(limit).toList(),
                degraded);
    }

    public record BasicRecall(String mode, String scope, List<Hit> exact, List<Hit> full) {
        public List<List<Hit>> rankings() {
            return List.of(exact, full);
        }
    }

    /** Keep independent rankings until all retrieval paths are available for one RRF fusion. */
    @Transactional(readOnly = true)
    public BasicRecall basicRecall(User user, Search query) {
        access.requireOwnedStory(query.storyId(), user);
        String mode = Objects.requireNonNullElse(query.mode(), "fact"),
                scope = Objects.requireNonNullElse(query.scope(), "bound");
        if (!Set.of("fact", "inspiration").contains(mode))
            throw error(HttpStatus.BAD_REQUEST, "MATERIAL_MODE_INVALID");
        String text = query.query().strip();
        var args = visibilityArgs(user, query.storyId(), scope);
        String exact =
                "select c.*,r.content_version"
                        + CURRENT
                        + visibility(scope)
                        + "and (c.title like ? escape '!' or c.text like ? escape '!' or"
                        + " exists(select 1 from material_entity_sources s join material_entities e"
                        + " on e.id=s.entity_id join material_entity_names n on n.entity_id=e.id"
                        + " where s.material_id=m.id and s.source_version=r.content_version and"
                        + " e.owner_id=? and n.name=?) or exists(select 1 from"
                        + " material_confirmed_annotations a where a.revision_id=r.id and"
                        + " a.owner_id=? and a.annotation_kind='TAG' and a.name=?)) order by"
                        + " c.seq,c.id limit 40";
        args.add("%" + like(text) + "%");
        args.add("%" + like(text) + "%");
        args.add(bytes(user.getId()));
        args.add(text);
        args.add(bytes(user.getId()));
        args.add(text);
        var exactHits = jdbc.query(exact, (r, n) -> hit(r, List.of("原文或已确认实体"), 0), args.toArray());
        var fullArgs = new ArrayList<Object>();
        fullArgs.add(text);
        fullArgs.addAll(visibilityArgs(user, query.storyId(), scope));
        fullArgs.add(text);
        var full =
                jdbc.query(
                        "select c.*,r.content_version,match(c.title,c.text) against (? in natural"
                                + " language mode) as relevance"
                                + CURRENT
                                + visibility(scope)
                                + "and match(c.title,c.text) against (? in natural language mode)>0"
                                + " order by relevance desc,c.id limit 40",
                        (r, n) -> hit(r, List.of("中文全文"), 0),
                        fullArgs.toArray());
        return new BasicRecall(mode, scope, exactHits, full);
    }

    static List<Hit> fuse(List<List<Hit>> rankings) {
        Map<String, Hit> hits = new LinkedHashMap<>();
        Map<String, Double> scores = new HashMap<>();
        Map<String, Set<String>> reasons = new HashMap<>();
        for (var ranking : rankings)
            for (int i = 0; i < ranking.size(); i++) {
                var h = ranking.get(i);
                hits.putIfAbsent(h.chunkId(), h);
                scores.merge(h.chunkId(), 1.0 / (60 + i + 1), Double::sum);
                reasons.computeIfAbsent(h.chunkId(), k -> new LinkedHashSet<>())
                        .addAll(h.reasons());
            }
        return hits.values().stream()
                .map(
                        h ->
                                new Hit(
                                        h.chunkId(),
                                        h.materialId(),
                                        h.revisionId(),
                                        h.sourceVersion(),
                                        h.title(),
                                        h.text(),
                                        h.start(),
                                        h.end(),
                                        List.copyOf(reasons.get(h.chunkId())),
                                        scores.get(h.chunkId())))
                .sorted(
                        Comparator.comparingDouble(Hit::rank)
                                .reversed()
                                .thenComparing(Hit::chunkId))
                .toList();
    }

    private Hit hit(java.sql.ResultSet r, List<String> reasons, double rank)
            throws java.sql.SQLException {
        return new Hit(
                r.getString("id"),
                uuid(r.getBytes("material_id")),
                r.getString("revision_id"),
                r.getLong("content_version"),
                r.getString("title"),
                r.getString("text"),
                r.getInt("start_cp"),
                r.getInt("end_cp"),
                reasons,
                rank);
    }

    @Transactional(readOnly = true)
    public Hit open(User user, String chunk) {
        return jdbc
                .query(
                        "select c.*,r.content_version"
                                + CURRENT
                                + "and c.id=? and (m.user_id=? or m.user_id is null)",
                        (r, n) -> hit(r, List.of("原文"), 0),
                        chunk,
                        bytes(user.getId()))
                .stream()
                .findFirst()
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE"));
    }

    @Transactional(readOnly = true)
    public List<Hit> neighbors(User user, String chunk) {
        var opened = open(user, chunk);
        Integer sequence =
                jdbc.queryForObject(
                        "select seq from material_evidence_chunks where id=?",
                        Integer.class,
                        chunk);
        return jdbc.query(
                "select c.*,r.content_version"
                        + CURRENT
                        + "and c.revision_id=? and c.seq between ? and ? and (m.user_id=? or"
                        + " m.user_id is null) order by c.seq",
                (r, n) -> hit(r, List.of("相邻原文"), 0),
                opened.revisionId(),
                Math.max(0, sequence - 1),
                sequence + 1,
                bytes(user.getId()));
    }

    @Transactional(readOnly = true)
    public List<com.ainovel.app.material.dto.MaterialSearchResultDto> legacySearch(
            User user, String query, int limit) {
        UUID owner = user == null ? null : user.getId();
        return jdbc.query(
                "select c.*,r.content_version"
                        + CURRENT
                        + "and (m.user_id=? or m.user_id is null) and (c.title like ? escape '!' or"
                        + " c.text like ? escape '!') order by c.seq,c.id limit ?",
                (r, n) ->
                        new com.ainovel.app.material.dto.MaterialSearchResultDto(
                                uuid(r.getBytes("material_id")),
                                r.getString("id"),
                                r.getString("title"),
                                r.getString("text"),
                                1.0,
                                r.getInt("seq"),
                                "keyword",
                                List.of("原文匹配")),
                owner == null ? null : bytes(owner),
                "%" + like(query) + "%",
                "%" + like(query) + "%",
                Math.min(30, limit));
    }

    @Transactional(readOnly = true)
    public List<UUID> visibleMaterialIds(User user, UUID story, String scope) {
        access.requireOwnedStory(story, user);
        return jdbc.query(
                "select m.id from materials m where m.status='approved' "
                        + visibility(scope)
                        + "order by m.id limit 501",
                (r, n) -> uuid(r.getBytes(1)),
                visibilityArgs(user, story, scope).toArray());
    }

    @Transactional(readOnly = true)
    public List<Hit> visibleHits(User user, Search query, List<String> ids) {
        if (ids.isEmpty()) return List.of();
        if (ids.size() > 40) throw error(HttpStatus.BAD_REQUEST, "CANDIDATE_LIMIT");
        String scope = Objects.requireNonNullElse(query.scope(), "bound");
        access.requireOwnedStory(query.storyId(), user);
        var args = visibilityArgs(user, query.storyId(), scope);
        args.addAll(ids);
        return jdbc.query(
                "select c.*,r.content_version"
                        + CURRENT
                        + visibility(scope)
                        + "and c.id in ("
                        + String.join(",", Collections.nCopies(ids.size(), "?"))
                        + ")",
                (r, n) -> hit(r, List.of("语义召回"), 0),
                args.toArray());
    }

    @Transactional(readOnly = true)
    public Settings settings(User user, UUID story) {
        access.requireOwnedStory(story, user);
        var rows =
                jdbc.queryForList(
                        "select * from material_work_settings where story_id=?", bytes(story));
        var bindings =
                jdbc.query(
                        "select material_id from material_work_bindings where story_id=? order by"
                                + " material_id",
                        (r, n) -> uuid(r.getBytes(1)),
                        bytes(story));
        if (rows.isEmpty()) return new Settings(0, "basic", false, false, false, bindings);
        var row = rows.getFirst();
        return new Settings(
                ((Number) row.get("row_version")).longValue(),
                (String) row.get("semantic_profile"),
                (Boolean) row.get("rerank_enabled"),
                (Boolean) row.get("hints_enabled"),
                (Boolean) row.get("checks_enabled"),
                bindings);
    }

    @Transactional
    public Settings saveSettings(User user, UUID story, SettingsWrite input) {
        access.requireOwnedStory(story, user);
        jdbc.update("insert ignore into material_work_settings(story_id) values(?)", bytes(story));
        jdbc.queryForObject(
                "select row_version from material_work_settings where story_id=? for update",
                Long.class,
                bytes(story));
        var replay = receipt(user, "work:" + story, input.requestKey(), input, Settings.class);
        if (replay != null) return replay;
        if (!Set.of("basic", "qwen-standard-1024-cp-v1", "qwen-flash-1024-cp-v1")
                .contains(input.semanticProfile()))
            throw error(HttpStatus.BAD_REQUEST, "INDEX_PROFILE_INVALID");
        var previous = settings(user, story).bindings();
        for (UUID id : input.bindings()) if (!previous.contains(id)) requireMaterial(user, id);
        int changed =
                jdbc.update(
                        "update material_work_settings set"
                            + " row_version=row_version+1,semantic_profile=?,rerank_enabled=?,hints_enabled=?,checks_enabled=?"
                            + " where story_id=? and row_version=?",
                        input.semanticProfile(),
                        input.rerank(),
                        input.hints(),
                        input.checks(),
                        bytes(story),
                        input.expectedVersion());
        if (changed != 1) throw error(HttpStatus.CONFLICT, "MATERIAL_SETTINGS_VERSION_CONFLICT");
        jdbc.update("delete from material_work_bindings where story_id=?", bytes(story));
        for (UUID id : new LinkedHashSet<>(input.bindings()))
            jdbc.update(
                    "insert into material_work_bindings(story_id,material_id) values(?,?)",
                    bytes(story),
                    bytes(id));
        var result = settings(user, story);
        remember(user, "work:" + story, input.requestKey(), input, result);
        return result;
    }

    private void requireMaterial(User user, UUID id) {
        if (jdbc.queryForObject(
                        "select count(*) from materials where id=? and status='approved' and"
                                + " (user_id=? or user_id is null)",
                        Long.class,
                        bytes(id),
                        bytes(user.getId()))
                != 1) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
    }

    @Transactional(readOnly = true)
    public Package scenePackage(User user, UUID manuscript, String scene) {
        access.requireOwnedManuscript(manuscript, user);
        UUID.fromString(scene);
        return jdbc
                .query(
                        "select row_version,pinned_json,excluded_json from material_scene_packages"
                                + " where manuscript_id=? and scene_id=?",
                        (r, n) ->
                                new Package(
                                        r.getLong(1),
                                        strings(r.getString(2)),
                                        strings(r.getString(3))),
                        bytes(manuscript),
                        scene)
                .stream()
                .findFirst()
                .orElse(new Package(0, List.of(), List.of()));
    }

    @Transactional
    public Package savePackage(User user, UUID manuscript, String scene, PackageWrite input) {
        var m = access.requireOwnedManuscript(manuscript, user);
        UUID.fromString(scene);
        jdbc.update(
                "insert ignore into"
                    + " material_scene_packages(manuscript_id,scene_id,pinned_json,excluded_json)"
                    + " values(?,?,'[]','[]')",
                bytes(manuscript),
                scene);
        jdbc.queryForObject(
                "select row_version from material_scene_packages where manuscript_id=? and"
                        + " scene_id=? for update",
                Long.class,
                bytes(manuscript),
                scene);
        String scope = "scene:" + manuscript + ":" + scene;
        var replay = receipt(user, scope, input.requestKey(), input, Package.class);
        if (replay != null) return replay;
        var bindings = settings(user, m.getOutline().getStory().getId()).bindings();
        for (String id : input.pinned()) {
            var source = open(user, id);
            if (!bindings.contains(source.materialId()))
                throw error(HttpStatus.CONFLICT, "SOURCE_NOT_BOUND");
        }
        if (input.pinned().stream().anyMatch(input.excluded()::contains))
            throw error(HttpStatus.BAD_REQUEST, "SOURCE_PIN_EXCLUDE_CONFLICT");
        int changed =
                jdbc.update(
                        "update material_scene_packages set"
                                + " row_version=row_version+1,pinned_json=?,excluded_json=? where"
                                + " manuscript_id=? and scene_id=? and row_version=?",
                        encode(input.pinned()),
                        encode(input.excluded()),
                        bytes(manuscript),
                        scene,
                        input.expectedVersion());
        if (changed != 1) throw error(HttpStatus.CONFLICT, "REFERENCE_PACKAGE_VERSION_CONFLICT");
        var result = scenePackage(user, manuscript, scene);
        remember(user, scope, input.requestKey(), input, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<SourceRevision> revisions(User user, UUID material, int page) {
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "SOURCE_PAGE_INVALID");
        return jdbc.query(
                "select r.id,r.content_version,r.title,r.created_at from material_revisions r join"
                        + " materials m on m.id=r.material_id where m.id=? and (m.user_id=? or"
                        + " (m.user_id is null and m.status='approved')) order by r.content_version"
                        + " desc limit 50 offset ?",
                (r, n) ->
                        new SourceRevision(
                                r.getString(1),
                                r.getLong(2),
                                r.getString(3),
                                r.getTimestamp(4).toInstant().toString()),
                bytes(material),
                bytes(user.getId()),
                page * 50);
    }

    @Transactional(readOnly = true)
    public RawRevision rawRevision(User user, String revision) {
        var rows =
                jdbc.query(
                        "select r.id,r.material_id,r.content_version,r.title,r.content from"
                            + " material_revisions r join materials m on m.id=r.material_id where"
                            + " r.id=? and (m.user_id=? or (m.user_id is null and"
                            + " m.status='approved'))",
                        (r, n) ->
                                new RawRevision(
                                        r.getString(1),
                                        uuid(r.getBytes(2)),
                                        r.getLong(3),
                                        r.getString(4),
                                        r.getString(5)),
                        revision,
                        bytes(user.getId()));
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
        return rows.getFirst();
    }

    @Transactional(readOnly = true)
    public List<SourceStatus> statuses(User user) {
        return jdbc.query(
                "select m.id,m.content_version,m.status,r.id as revision_id,b.status as"
                    + " basic_status,(select count(*) from material_evidence_chunks c where"
                    + " c.revision_id=r.id) as chunks,(select"
                    + " group_concat(concat(j.profile,':',j.status) order by j.profile separator"
                    + " ',') from material_semantic_jobs j where j.revision_id=r.id) as semantic"
                    + " from materials m join material_revisions r on r.material_id=m.id and"
                    + " r.content_version=m.content_version left join material_basic_jobs b on"
                    + " b.revision_id=r.id where m.user_id=? order by m.updated_at desc",
                (r, n) ->
                        new SourceStatus(
                                uuid(r.getBytes("id")),
                                r.getString("revision_id"),
                                r.getLong("content_version"),
                                "SAVED",
                                r.getString("status"),
                                !"approved".equals(r.getString("status"))
                                        ? "WITHHELD"
                                        : !"COMPLETED".equals(r.getString("basic_status"))
                                                ? Objects.requireNonNullElse(
                                                        r.getString("basic_status"), "QUEUED")
                                                : r.getInt("chunks") > 0 ? "SEARCHABLE" : "EMPTY",
                                Objects.requireNonNullElse(r.getString("semantic"), "NOT_ENABLED")),
                bytes(user.getId()));
    }

    @Transactional
    public String citation(User user, UUID manuscript, String scene, CitationWrite input) {
        UUID.fromString(scene);
        jdbc.queryForObject(
                "select id from users where id=? for update", byte[].class, bytes(user.getId()));
        var replay = receipt(user, "citation", input.requestKey(), input, String.class);
        if (replay != null) return replay;
        var locked =
                jdbc.queryForList(
                        "select m.version from manuscripts m join outlines o on o.id=m.outline_id"
                            + " join stories s on s.id=o.story_id where m.id=? and s.user_id=? for"
                            + " update",
                        bytes(manuscript),
                        bytes(user.getId()));
        if (locked.isEmpty()) throw error(HttpStatus.NOT_FOUND, "MANUSCRIPT_NOT_FOUND");
        if (input.expectedManuscriptVersion() != null
                && input.expectedManuscriptVersion()
                        != ((Number) locked.getFirst().get("version")).longValue())
            throw error(HttpStatus.CONFLICT, "CITATION_BODY_STALE");
        var m = access.requireOwnedManuscript(manuscript, user);
        if (!Set.of("CONFIRMED", "POSSIBLE").contains(input.relationType()))
            throw error(HttpStatus.BAD_REQUEST, "CITATION_TYPE_INVALID");
        if (m.getCurrentBranchId() == null
                || !m.getCurrentBranchId().toString().equals(input.branchId()))
            throw error(HttpStatus.CONFLICT, "CITATION_BRANCH_STALE");
        var revisions =
                jdbc.queryForList(
                        "select r.content from material_revisions r join materials s on"
                            + " s.id=r.material_id and s.content_version=r.content_version where"
                            + " r.id=? and s.status='approved' and (s.user_id=? or s.user_id is"
                            + " null)",
                        input.revisionId(),
                        bytes(user.getId()));
        if (revisions.isEmpty()) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
        if (!EvidenceText.slice(
                        (String) revisions.getFirst().get("content"), input.start(), input.end())
                .equals(input.quote()))
            throw error(HttpStatus.BAD_REQUEST, "CITATION_QUOTE_MISMATCH");
        var versions =
                jdbc.queryForList(
                        "select sections_json from manuscript_versions where id=? and"
                                + " manuscript_id=? and branch_id=?",
                        bytes(UUID.fromString(input.bodyVersion())),
                        bytes(manuscript),
                        bytes(UUID.fromString(input.branchId())));
        if (versions.isEmpty()) throw error(HttpStatus.NOT_FOUND, "BODY_VERSION_UNAVAILABLE");
        var snapshot =
                json.convertValue(
                        parse(Objects.toString(versions.getFirst().get("sections_json"))),
                        new TypeReference<Map<String, String>>() {});
        if (!Objects.equals(snapshot.get(scene), contents.readScene(m, UUID.fromString(scene))))
            throw error(HttpStatus.CONFLICT, "CITATION_BODY_STALE");
        com.ainovel.app.narrative.NarrativeDtos.Evidence bodyEvidence = null;
        if (input.bodyBlockId() != null)
            bodyEvidence =
                    com.ainovel.app.narrative.NarrativeText.resolve(
                            com.ainovel.app.narrative.NarrativeText.blocks(snapshot.get(scene)),
                            new com.ainovel.app.narrative.NarrativeDtos.Evidence(
                                    input.bodyBlockId(), input.bodyQuote(), null, null));
        else if (!Objects.toString(snapshot.get(scene), "").contains(input.bodyQuote()))
            throw error(HttpStatus.CONFLICT, "CITATION_BODY_STALE");
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "insert into"
                    + " material_source_links(id,owner_id,manuscript_id,scene_id,branch_id,body_version,revision_id,relation_type,start_cp,end_cp,quote,body_quote,request_key,request_hash,body_block_id,body_start_cp,body_end_cp,body_conversion_version,state)"
                    + " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                id,
                bytes(user.getId()),
                bytes(manuscript),
                scene,
                input.branchId(),
                input.bodyVersion(),
                input.revisionId(),
                input.relationType(),
                input.start(),
                input.end(),
                input.quote(),
                input.bodyQuote(),
                input.requestKey(),
                EvidenceText.hash(encode(input)),
                bodyEvidence == null ? null : bodyEvidence.blockId(),
                bodyEvidence == null ? null : bodyEvidence.start(),
                bodyEvidence == null ? null : bodyEvidence.end(),
                bodyEvidence == null
                        ? null
                        : com.ainovel.app.common.text.RichTextProjector.Policy.EVIDENCE_V1
                                .version(),
                bodyEvidence == null ? "REVIEW_REQUIRED" : "CURRENT");
        remember(user, "citation", input.requestKey(), input, id);
        return id;
    }

    @Transactional(readOnly = true)
    public CitationBody citationBody(User user, UUID manuscript, String scene) {
        var m = access.requireOwnedManuscript(manuscript, user);
        return new CitationBody(
                m.getCurrentBranchId(),
                m.getVersion(),
                com.ainovel.app.narrative.NarrativeText.blocks(
                        contents.readScene(m, UUID.fromString(scene))));
    }

    @Transactional
    public List<Map<String, Object>> citations(User user, UUID manuscript) {
        access.requireOwnedManuscript(manuscript, user);
        jdbc.update(
                "update material_source_links l join material_revisions r on r.id=l.revision_id"
                    + " left join materials m on m.id=r.material_id set l.state='REVIEW_REQUIRED'"
                    + " where l.manuscript_id=? and (m.id is null or m.status<>'approved' or"
                    + " m.content_version<>r.content_version or (m.user_id is not null and"
                    + " m.user_id<>?))",
                bytes(manuscript),
                bytes(user.getId()));
        return jdbc.queryForList(
                "select"
                    + " l.id,l.scene_id,l.branch_id,l.body_version,l.revision_id,l.relation_type,l.start_cp,l.end_cp,case"
                    + " when m.status='approved' and (m.user_id=? or m.user_id is null) then"
                    + " l.quote else null end as"
                    + " quote,l.body_quote,l.state,l.body_block_id,l.body_start_cp,l.body_end_cp,l.body_conversion_version,l.report_task_id,l.report_finding_index"
                    + " from material_source_links l join material_revisions r on"
                    + " r.id=l.revision_id left join materials m on m.id=r.material_id where"
                    + " l.manuscript_id=? and l.owner_id=? order by l.created_at desc",
                bytes(user.getId()),
                bytes(manuscript),
                bytes(user.getId()));
    }

    @Transactional
    public void recordReferences(
            User user, UUID manuscript, UUID scene, UUID branch, UUID body, List<Hit> references) {
        for (var h : references) {
            boolean current = validateFrozenReference(user, h);
            String key = "ref:" + body + ":" + h.chunkId();
            jdbc.update(
                    "insert ignore into"
                        + " material_source_links(id,owner_id,manuscript_id,scene_id,branch_id,body_version,revision_id,relation_type,start_cp,end_cp,quote,body_quote,request_key,request_hash,state)"
                        + " values(?,?,?,?,?,?,?,'REFERENCE',?,?,?,'',?,?,?)",
                    UUID.randomUUID().toString(),
                    bytes(user.getId()),
                    bytes(manuscript),
                    scene.toString(),
                    branch.toString(),
                    body.toString(),
                    h.revisionId(),
                    h.start(),
                    h.end(),
                    h.text(),
                    key,
                    EvidenceText.hash(encode(h)),
                    current ? "CURRENT" : "REVIEW_REQUIRED");
        }
    }

    @Transactional(readOnly = true)
    public boolean validateFrozenReference(User user, Hit hit) {
        var rows =
                jdbc.queryForList(
                        "select r.content,r.content_version,m.content_version as current_version"
                            + " from material_revisions r join materials m on m.id=r.material_id"
                            + " where r.id=? and r.material_id=? and m.status='approved' and"
                            + " (m.user_id=? or m.user_id is null)",
                        hit.revisionId(),
                        bytes(hit.materialId()),
                        bytes(user.getId()));
        if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
        var source = rows.getFirst();
        if (((Number) source.get("content_version")).longValue() != hit.sourceVersion()
                || !EvidenceText.slice((String) source.get("content"), hit.start(), hit.end())
                        .equals(hit.text()))
            throw error(HttpStatus.CONFLICT, "FROZEN_REFERENCE_INVALID");
        return ((Number) source.get("current_version")).longValue() == hit.sourceVersion();
    }

    private com.fasterxml.jackson.databind.JsonNode parse(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException("STORED_EVIDENCE_INVALID");
        }
    }

    @Transactional
    public Entity createEntity(User user, EntityWrite input) {
        jdbc.queryForObject(
                "select id from users where id=? for update", byte[].class, bytes(user.getId()));
        var replay = receipt(user, "entity", input.requestKey(), input, Entity.class);
        if (replay != null) return replay;
        for (var source : input.materials().entrySet()) {
            if (jdbc.queryForObject(
                            "select count(*) from materials where id=? and (user_id=? or (user_id"
                                    + " is null and status='approved'))",
                            Integer.class,
                            bytes(source.getKey()),
                            bytes(user.getId()))
                    != 1) throw error(HttpStatus.NOT_FOUND, "SOURCE_UNAVAILABLE");
            Long version =
                    jdbc.queryForObject(
                            "select content_version from materials where id=? for update",
                            Long.class,
                            bytes(source.getKey()));
            if (!Objects.equals(version, source.getValue()))
                throw error(HttpStatus.CONFLICT, "SOURCE_VERSION_CONFLICT");
        }
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "insert into material_entities(id,owner_id,name) values(?,?,?)",
                id,
                bytes(user.getId()),
                input.name());
        var names = new LinkedHashSet<>(input.aliases());
        names.add(input.name());
        for (String name : names)
            jdbc.update("insert into material_entity_names(entity_id,name) values(?,?)", id, name);
        for (var source : input.materials().entrySet())
            jdbc.update(
                    "insert into material_entity_sources(entity_id,material_id,source_version)"
                            + " values(?,?,?)",
                    id,
                    bytes(source.getKey()),
                    source.getValue());
        var result = new Entity(id, input.name(), List.copyOf(names));
        jdbc.update(
                "update material_hint_slots set cache_key=null,result_json=null where owner_id=?",
                bytes(user.getId()));
        remember(user, "entity", input.requestKey(), input, result);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Entity> entities(User user, int page) {
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "PAGE_RANGE_INVALID");
        return jdbc.query(
                "select id,name from material_entities where owner_id=? order by name,id limit 50"
                        + " offset ?",
                (r, n) ->
                        new Entity(
                                r.getString(1),
                                r.getString(2),
                                jdbc.query(
                                        "select name from material_entity_names where entity_id=?"
                                                + " order by name",
                                        (a, i) -> a.getString(1),
                                        r.getString(1))),
                bytes(user.getId()),
                page * 50);
    }

    @Transactional(readOnly = true)
    public List<Hit> entitySources(User user, UUID story, String entity, int page) {
        if (page < 0 || page > 100000) throw error(HttpStatus.BAD_REQUEST, "PAGE_RANGE_INVALID");
        access.requireOwnedStory(story, user);
        return jdbc.query(
                "select c.*,r.content_version"
                        + CURRENT
                        + "and exists(select 1 from material_entity_sources s join"
                        + " material_entities e on e.id=s.entity_id where s.material_id=m.id and"
                        + " s.source_version=r.content_version and e.id=? and e.owner_id=?) and"
                        + " (m.user_id=? or m.user_id is null) order by m.id,c.seq limit 50 offset"
                        + " ?",
                (r, n) -> hit(r, List.of("已确认实体关联"), 0),
                entity,
                bytes(user.getId()),
                bytes(user.getId()),
                page * 50);
    }

    @Transactional(readOnly = true)
    public List<Duplicate> duplicates(User user) {
        var size =
                jdbc.queryForObject(
                        "select coalesce(sum(octet_length(content)),0) from materials where"
                                + " user_id=?",
                        Long.class,
                        bytes(user.getId()));
        if (size != null && size > 4_194_304)
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "DUPLICATE_TEXT_LIMIT");
        var rows =
                jdbc.queryForList(
                        "select id,content from materials where user_id=? order by id limit 201",
                        bytes(user.getId()));
        if (rows.size() > 200) throw error(HttpStatus.PAYLOAD_TOO_LARGE, "DUPLICATE_BATCH_LIMIT");
        List<String> normalized = new ArrayList<>();
        List<Set<String>> fragments = new ArrayList<>();
        for (var row : rows) {
            String text = Objects.toString(row.get("content"), "");
            normalized.add(EvidenceText.normalize(text));
            fragments.add(EvidenceText.shingles(text));
        }
        List<Duplicate> result = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++)
            for (int j = i + 1; j < rows.size(); j++) {
                String a = Objects.toString(rows.get(i).get("content"), ""),
                        b = Objects.toString(rows.get(j).get("content"), "");
                if (a.isBlank() || b.isBlank()) continue;
                String kind = null;
                if (a.equals(b)) kind = "EXACT";
                else if (normalized.get(i).equals(normalized.get(j))) kind = "FORMATTING";
                else {
                    long shared =
                            fragments.get(i).stream()
                                    .filter(fragments.get(j)::contains)
                                    .limit(3)
                                    .count();
                    if (shared >= 3) kind = "PARTIAL_OVERLAP";
                }
                if (kind != null) {
                    result.add(
                            new Duplicate(
                                    uuid((byte[]) rows.get(i).get("id")),
                                    uuid((byte[]) rows.get(j).get("id")),
                                    kind,
                                    EvidenceText.slice(
                                            a, 0, Math.min(2000, a.codePointCount(0, a.length()))),
                                    EvidenceText.slice(
                                            b,
                                            0,
                                            Math.min(2000, b.codePointCount(0, b.length())))));
                    if (result.size() >= 100) return result;
                }
            }
        return result;
    }

    private <T> T receipt(User user, String scope, String key, Object input, Class<T> type) {
        var rows =
                jdbc.queryForList(
                        "select request_hash,result_json from material_mutation_receipts where"
                                + " owner_id=? and scope=? and request_key=? for update",
                        bytes(user.getId()),
                        scope,
                        key);
        if (rows.isEmpty()) return null;
        if (!EvidenceText.hash(encode(input)).equals(rows.getFirst().get("request_hash")))
            throw error(HttpStatus.CONFLICT, "MATERIAL_REQUEST_CONFLICT");
        try {
            return json.readValue((String) rows.getFirst().get("result_json"), type);
        } catch (Exception invalid) {
            throw new IllegalStateException(invalid);
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

    private List<String> strings(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String encode(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String like(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    static ApiStatusException error(HttpStatus status, String code) {
        return new ApiStatusException(status, code);
    }
}
