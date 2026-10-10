package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import static org.junit.jupiter.api.Assertions.*;

import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.integration.*;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.material.*;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.*;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.*;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit manual acceptance only: isolated MySQL, real production recall, no answer labels. */
@EnabledIfEnvironmentVariable(named = "AIENIE_SELECTION_MODE", matches = "basic|live")
@DataJpaTest(
        showSql = false,
        properties = {
            "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none",
            "spring.jpa.open-in-view=false", "spring.sql.init.mode=never"
        })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
    MaterialEvidenceService.class,
    ManuscriptContentService.class,
    ResourceAccessGuard.class,
    JsonColumnCodec.class,
    MaterialSelectionMysqlTest.Beans.class
})
class MaterialSelectionMysqlTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        if (url == null
                || !url.matches(
                        "jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))
            throw new IllegalArgumentException("Isolated selection schema required");
        properties.add("spring.datasource.url", () -> url);
        properties.add(
                "spring.datasource.username", () -> System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        properties.add(
                "spring.datasource.password", () -> System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        properties.add(
                "spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQLDialect");
    }

    @TestConfiguration
    static class Beans {
        @Bean
        ObjectMapper json() {
            return new ObjectMapper().findAndRegisterModules();
        }

        @Bean
        TransactionTemplate transactions(PlatformTransactionManager manager) {
            return new TransactionTemplate(manager);
        }
    }

    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired MaterialEvidenceService sources;
    @Autowired ObjectMapper json;
    @Autowired Environment environment;
    @MockitoBean com.ainovel.app.common.CurrentUserResolver userResolver;

    record Fixture(
            User owner, UUID story, Map<String, JsonNode> chunks, Map<String, String> stableIds) {}

    @Test
    void collectFrozenRankingsWithoutReadingAnswers() throws Exception {
        Path dataset = Path.of("src/test/resources/material-selection-20261003");
        JsonNode manifest = json.readTree(dataset.resolve("manifest.json").toFile());
        for (var entry : manifest.path("sha256").properties()) {
            // Hash bytes only. The collector never decodes development or holdout answer labels.
            assertEquals(
                    entry.getValue().asText(),
                    EvidenceText.hash(
                            Files.readString(
                                    dataset.resolve(entry.getKey()), StandardCharsets.UTF_8)));
        }
        String run = Objects.requireNonNull(System.getenv("AIENIE_SELECTION_RUN"));
        if (!run.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Stable run required");
        String split = Objects.requireNonNull(System.getenv("AIENIE_SELECTION_SPLIT"));
        if (!Set.of("development", "holdout").contains(split))
            throw new IllegalArgumentException("Split required");
        Path output =
                Path.of(Objects.requireNonNull(System.getenv("AIENIE_SELECTION_OUTPUT")))
                        .toAbsolutePath()
                        .normalize();
        Path artifacts =
                Path.of("../artifacts/retrieval-evidence-20261003").toAbsolutePath().normalize();
        if (!output.startsWith(artifacts))
            throw new IllegalArgumentException("Selection artifact directory required");
        boolean basicOnly = "basic".equals(System.getenv("AIENIE_SELECTION_MODE"));
        Long uid =
                basicOnly
                        ? null
                        : Long.valueOf(
                                Objects.requireNonNull(System.getenv("AIENIE_SELECTION_USER_ID")));
        if (uid != null && uid <= 0)
            throw new IllegalArgumentException("Authorized gateway user required");
        var corpus = json.readTree(dataset.resolve("corpus.json").toFile());
        var fixture =
                fixture(run, corpus, manifest.path("sha256").path("corpus.json").asText(), uid);
        var queries = new ArrayList<MaterialSelectionCollector.Query>();
        for (var query : json.readTree(dataset.resolve(split + "-queries.json").toFile()))
            queries.add(
                    new MaterialSelectionCollector.Query(
                            query.path("id").asText(),
                            query.path("mode").asText(),
                            query.path("query").asText()));
        assertEquals(split.equals("development") ? 120 : 80, queries.size());
        var ports = new SelectionPorts(fixture, run, manifest, output.getParent(), basicOnly);
        try {
            new MaterialSelectionCollector(
                            json,
                            ports,
                            output,
                            manifest,
                            run,
                            hit ->
                                    Objects.requireNonNull(
                                            fixture.stableIds().get(hit.chunkId()),
                                            "Unknown dataset chunk"))
                    .collect(queries, basicOnly);
        } finally {
            ports.close();
        }
        json.writerWithDefaultPrettyPrinter()
                .writeValue(
                        output.resolveSibling(output.getFileName() + ".provenance.json").toFile(),
                        Map.of(
                                "run",
                                run,
                                "split",
                                split,
                                "schema",
                                jdbc.queryForObject("select database()", String.class),
                                "owner",
                                fixture.owner().getId(),
                                "story",
                                fixture.story(),
                                "chunks",
                                fixture.stableIds(),
                                "supplierCallsEnabled",
                                !basicOnly,
                                "datasetSha256",
                                manifest.path("sha256")));
    }

    private Fixture fixture(String run, JsonNode corpus, String corpusHash, Long uid) {
        String marker = "selection-" + EvidenceText.hash(run).substring(0, 24);
        String materialMarker = "SEL" + EvidenceText.hash(run).substring(0, 12);
        var owner =
                tx.execute(
                        status -> {
                            var existing =
                                    em.createQuery(
                                                    "select u from User u where u.username=:name",
                                                    User.class)
                                            .setParameter("name", marker)
                                            .getResultList();
                            if (!existing.isEmpty()) {
                                User user = existing.getFirst();
                                if (uid != null && user.getRemoteUid() == null)
                                    user.setRemoteUid(uid);
                                if (uid != null)
                                    MaterialNativeClient.requireOriginalUser(user, uid);
                                return user;
                            }
                            User user = new User();
                            user.setUsername(marker);
                            user.setEmail(marker + "@example.invalid");
                            user.setPasswordHash("selection-disabled-login");
                            user.setRemoteUid(uid);
                            em.persist(user);
                            User foreign = new User();
                            foreign.setUsername(marker + "-foreign");
                            foreign.setEmail(marker + "-foreign@example.invalid");
                            foreign.setPasswordHash("selection-disabled-login");
                            em.persist(foreign);
                            Story story = new Story();
                            story.setUser(user);
                            story.setTitle(marker);
                            story.setSynopsis(corpusHash);
                            story.setStatus("draft");
                            em.persist(story);
                            var grouped = new LinkedHashMap<String, List<JsonNode>>();
                            for (var source : corpus)
                                grouped.computeIfAbsent(
                                                source.path("material").asText(),
                                                key -> new ArrayList<>())
                                        .add(source);
                            for (var group : grouped.values()) {
                                group.sort(
                                        Comparator.comparingInt(
                                                source -> source.path("version").asInt()));
                                var latest = group.getLast();
                                Material material = new Material();
                                material.setSource(materialMarker);
                                material.setSummary(latest.path("material").asText());
                                material.setUser(
                                        latest.path("visibility").asText().equals("OTHER_OWNER")
                                                ? foreign
                                                : user);
                                material.setStatus(
                                        latest.path("visibility").asText().equals("REVOKED")
                                                ? "pending"
                                                : "approved");
                                material.setTagsJson("[]");
                                material.setType("setting");
                                material.setTitle(latest.path("title").asText());
                                material.setContent(latest.path("text").asText());
                                material.setContentVersion(latest.path("version").asLong());
                                em.persist(material);
                                em.flush();
                                for (var source : group) {
                                    material.setTitle(source.path("title").asText());
                                    material.setContent(source.path("text").asText());
                                    material.setContentVersion(source.path("version").asLong());
                                    sources.capture(material);
                                }
                                // Deliberately include foreign/withdrawn bindings to verify
                                // filtering even with stale bindings.
                                jdbc.update(
                                        "insert into material_work_bindings(story_id,material_id)"
                                                + " values(?,?)",
                                        bytes(story.getId()),
                                        bytes(material.getId()));
                                if (latest.path("visibility").asText().equals("CURRENT")) {
                                    var aliases = new ArrayList<String>();
                                    latest.path("aliases")
                                            .forEach(alias -> aliases.add(alias.asText()));
                                    sources.createEntity(
                                            user,
                                            new EntityWrite(
                                                    latest.path("entity").asText(),
                                                    aliases,
                                                    Map.of(
                                                            material.getId(),
                                                            material.getContentVersion()),
                                                    "entity:" + latest.path("id").asText()));
                                }
                            }
                            em.flush();
                            return user;
                        });
        var story =
                jdbc.queryForList(
                        "select id,synopsis from stories where user_id=? and title=?",
                        bytes(owner.getId()),
                        marker);
        assertEquals(1, story.size());
        assertEquals(corpusHash, story.getFirst().get("synopsis"));
        UUID storyId = uuid((byte[]) story.getFirst().get("id"));
        for (int i = 0; i < 2000; i++) {
            Integer pending =
                    jdbc.queryForObject(
                            "select count(*) from material_basic_jobs j join material_revisions r"
                                + " on r.id=j.revision_id join materials m on m.id=r.material_id"
                                + " where m.source=? and j.status<>'COMPLETED'",
                            Integer.class,
                            materialMarker);
            if (pending == 0) break;
            sources.backfill(2);
        }
        Map<String, JsonNode> sourceIds = new HashMap<>();
        for (var source : corpus)
            sourceIds.put(
                    source.path("material").asText() + ":" + source.path("version").asLong(),
                    source);
        Map<String, JsonNode> chunks = new LinkedHashMap<>();
        Map<String, String> stableIds = new LinkedHashMap<>();
        var rows =
                jdbc.queryForList(
                        "select c.*,r.content_version,r.content,m.summary from"
                                + " material_evidence_chunks c join material_revisions r on"
                                + " r.id=c.revision_id join materials m on m.id=r.material_id where"
                                + " m.source=? order by m.summary,r.content_version,c.seq",
                        materialMarker);
        for (var row : rows) {
            var source =
                    Objects.requireNonNull(
                            sourceIds.get(row.get("summary") + ":" + row.get("content_version")));
            assertEquals(source.path("text").asText(), row.get("content"));
            String id = (String) row.get("id");
            chunks.put(id, source);
            stableIds.put(id, source.path("id").asText() + ":" + row.get("seq"));
        }
        assertFalse(chunks.isEmpty());
        assertEquals(75, sources.visibleMaterialIds(owner, storyId, "bound").size());
        return new Fixture(owner, storyId, chunks, stableIds);
    }

    private final class SelectionPorts implements MaterialSelectionCollector.Ports, AutoCloseable {
        private final Fixture fixture;
        private final String run;
        private final String corpusHash;
        private final Path directory;
        private final Map<String, QdrantMaterialVectorIndex> indexes = new HashMap<>();
        private final Set<String> indexed = new HashSet<>();
        private final AiGatewayGrpcClient gateway;
        private final MaterialNativeClient nativeClient;

        SelectionPorts(
                Fixture fixture, String run, JsonNode manifest, Path directory, boolean basicOnly) {
            this.fixture = fixture;
            this.run = run;
            this.directory = directory;
            this.corpusHash = manifest.path("sha256").path("corpus.json").asText();
            if (basicOnly) {
                gateway = null;
                nativeClient = null;
                return;
            }
            var properties =
                    Binder.get(environment)
                            .bind("app.external", Bindable.of(ExternalServiceProperties.class))
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Private gateway configuration required"));
            if (!"ainovel".equals(properties.getProjectKey())
                    || !properties.getGrpc().isTlsEnabled()
                    || properties.getGrpc().isPlaintextEnabled()
                    || !"static://localaiservice.testhut.top:22011"
                            .equals(properties.getAiserviceGrpc().getAddress())
                    || properties.getSecurity().getAi().getHmacCaller().isBlank()
                    || properties.getSecurity().getAi().getHmacSecret().isBlank())
                throw new IllegalStateException(
                        "Matrix develop TLS gateway and credentials required");
            gateway = new AiGatewayGrpcClient(properties, new GrpcChannelFactory(properties));
            nativeClient = new MaterialNativeClient(gateway, run);
            String host = environment.getProperty("qdrant.host"),
                    key = environment.getProperty("qdrant.api-key", "");
            int port = environment.getProperty("qdrant.http-port", Integer.class, 0);
            if (!"http://localqdrant.testhut.top".equals(host) || port != 26333)
                throw new IllegalStateException("Matrix Qdrant endpoint required");
            for (String id : MaterialSelectionCollector.PROFILES) {
                String collection =
                        "ainovel_eval_"
                                + EvidenceText.hash(run).substring(0, 12)
                                + "_"
                                + corpusHash.substring(0, 12)
                                + "_"
                                + (id.contains("flash") ? "flash" : "standard")
                                + "_1024_cp900_120_document_v1";
                indexes.put(
                        id,
                        new QdrantMaterialVectorIndex(
                                json, host, port, collection, key, 2000, 5000, true));
            }
        }

        private Search search(MaterialSelectionCollector.Query q) {
            return new Search(q.text(), fixture.story(), q.mode(), "bound", 40);
        }

        public MaterialEvidenceService.BasicRecall basic(MaterialSelectionCollector.Query query) {
            var result = sources.basicRecall(fixture.owner(), search(query));
            result.rankings().forEach(this::assertVisible);
            return result;
        }

        private void assertVisible(List<Hit> hits) {
            for (var hit : hits)
                assertEquals(
                        "CURRENT",
                        Objects.requireNonNull(fixture.chunks().get(hit.chunkId()))
                                .path("visibility")
                                .asText(),
                        "Permission or version leak");
        }

        public List<Hit> semantic(
                MaterialSelectionCollector.Query query, String profile, String requestId) {
            if (nativeClient == null) throw new IllegalStateException("Supplier calls disabled");
            index(profile);
            var vector =
                    nativeClient
                            .embed(
                                    fixture.owner(),
                                    requestId,
                                    MaterialNativeClient.profile(profile),
                                    List.of(query.text()),
                                    "query")
                            .getFirst();
            var matches =
                    indexes.get(profile)
                            .searchWithin(
                                    vector,
                                    40,
                                    fixture.owner().getId(),
                                    sources.visibleMaterialIds(
                                            fixture.owner(), fixture.story(), "bound"));
            var visible =
                    sources.visibleHits(
                            fixture.owner(),
                            search(query),
                            matches.stream().map(VectorMatch::chunkId).toList());
            var byId = new HashMap<String, Hit>();
            visible.forEach(hit -> byId.put(hit.chunkId(), hit));
            var ordered =
                    matches.stream()
                            .filter(match -> byId.containsKey(match.chunkId()))
                            .map(match -> byId.get(match.chunkId()))
                            .toList();
            assertVisible(ordered);
            return ordered;
        }

        private void index(String profile) {
            if (indexed.contains(profile)) return;
            var chunks =
                    fixture.chunks().entrySet().stream()
                            .filter(
                                    entry ->
                                            "CURRENT"
                                                    .equals(
                                                            entry.getValue()
                                                                    .path("visibility")
                                                                    .asText()))
                            .map(Map.Entry::getKey)
                            .sorted(Comparator.comparing(fixture.stableIds()::get))
                            .toList();
            for (int start = 0; start < chunks.size(); start += 20) {
                int batch = start / 20;
                var ids = chunks.subList(start, Math.min(start + 20, chunks.size()));
                var current = ids.stream().map(id -> sources.open(fixture.owner(), id)).toList();
                assertVisible(current);
                Path checkpoint =
                        directory.resolve(
                                "vectors-"
                                        + EvidenceText.hash(run).substring(0, 12)
                                        + "-"
                                        + corpusHash.substring(0, 12)
                                        + "-"
                                        + profile
                                        + "-"
                                        + batch
                                        + ".json");
                String request =
                        "selection-index:"
                                + EvidenceText.hash(run).substring(0, 12)
                                + ":"
                                + corpusHash.substring(0, 12)
                                + ":"
                                + profile
                                + ":"
                                + batch;
                try {
                    List<float[]> vectors;
                    if (Files.exists(checkpoint)) {
                        var saved = json.readTree(checkpoint.toFile());
                        if (saved.has("failure"))
                            throw new IllegalStateException(
                                    "Index request requires reconciliation");
                        assertEquals(request, saved.path("request").asText());
                        assertEquals(
                                fixture.owner().getRemoteUid().longValue(),
                                saved.path("user").asLong());
                        assertEquals(
                                ids,
                                json.convertValue(
                                        saved.path("ids"),
                                        new com.fasterxml.jackson.core.type.TypeReference<
                                                List<String>>() {}));
                        vectors =
                                json.convertValue(
                                        saved.path("vectors"),
                                        new com.fasterxml.jackson.core.type.TypeReference<
                                                List<float[]>>() {});
                    } else {
                        vectors =
                                nativeClient.embed(
                                        fixture.owner(),
                                        request,
                                        MaterialNativeClient.profile(profile),
                                        current.stream().map(Hit::text).toList(),
                                        "document");
                        MaterialSelectionCollector.writeCheckpoint(
                                json,
                                checkpoint,
                                Map.of(
                                        "request",
                                        request,
                                        "user",
                                        fixture.owner().getRemoteUid(),
                                        "ids",
                                        ids,
                                        "vectors",
                                        vectors));
                    }
                    assertEquals(current.size(), vectors.size());
                    for (int i = 0; i < current.size(); i++) {
                        var hit = sources.open(fixture.owner(), current.get(i).chunkId());
                        indexes.get(profile)
                                .upsert(
                                        new MaterialChunk(
                                                hit.chunkId(),
                                                hit.materialId(),
                                                hit.title(),
                                                hit.text(),
                                                Integer.parseInt(
                                                        fixture.stableIds()
                                                                .get(hit.chunkId())
                                                                .replaceFirst(".*:", "")),
                                                "",
                                                fixture.owner().getId(),
                                                "approved"),
                                        vectors.get(i));
                    }
                } catch (Exception failure) {
                    if (!Files.exists(checkpoint)) {
                        try {
                            MaterialSelectionCollector.writeCheckpoint(
                                    json,
                                    checkpoint,
                                    Map.of(
                                            "request",
                                            request,
                                            "failure",
                                            failure.getClass().getSimpleName()));
                        } catch (Exception ignored) {
                            /* Original gateway identity still prevents duplicate inference. */
                        }
                    }
                    throw new IllegalStateException(
                            "Selection index stopped; original request retained", failure);
                }
            }
            indexed.add(profile);
        }

        public List<Hit> rerank(
                MaterialSelectionCollector.Query query, List<Hit> candidates, String requestId) {
            if (nativeClient == null) throw new IllegalStateException("Supplier calls disabled");
            var fresh =
                    sources.visibleHits(
                            fixture.owner(),
                            search(query),
                            candidates.stream().map(Hit::chunkId).toList());
            assertEquals(candidates.size(), fresh.size(), "Permissions changed before rerank");
            assertVisible(fresh);
            var response =
                    nativeClient.rerank(
                            fixture.owner(),
                            requestId,
                            query.text(),
                            candidates.stream().map(Hit::text).toList(),
                            query.mode(),
                            candidates.size());
            return response.getResultsList().stream()
                    .map(result -> candidates.get(result.getIndex()))
                    .toList();
        }

        public void close() {
            if (gateway != null) gateway.shutdown();
        }
    }
}
