package com.ainovel.app.material;

import com.ainovel.app.admin.AdminOperationsReadRepository;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.ManuscriptContentService;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.material.repo.MaterialRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in synthetic growth checks against a disposable, explicitly named MySQL schema. */
@EnabledIfEnvironmentVariable(named = "AIENIE_AUDIT_MYSQL_URL", matches = "jdbc:mysql:.*")
class RemediationCapacityExternalMysqlTest {
    @Test void sceneWritesAdminPagesAndDuplicateBudgetsAtPlannedSizes() throws Exception {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"));
        var ds = new DriverManagerDataSource(url, System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),
                System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        long startConnections = globalStatus(jdbc, "Threads_connected");
        long startLockWaitMillis = globalStatus(jdbc, "Innodb_row_lock_time");
        ManuscriptContentService content = new ManuscriptContentService(jdbc, new JsonColumnCodec(new ObjectMapper()), true);
        for (int size : List.of(10, 100, 1000)) {
            UUID manuscriptId = UUID.randomUUID(), changedScene = null;
            jdbc.update("insert into manuscripts(id,sections_json,content_storage_version,version) values(?,'{}',2,0)", bytes(manuscriptId));
            try (Connection connection = ds.getConnection(); PreparedStatement statement = connection.prepareStatement(
                    "insert into manuscript_scene_contents(manuscript_id,scene_id,content,word_count,updated_at) values(?,?,?,?,CURRENT_TIMESTAMP)")) {
                for (int i = 0; i < size; i++) {
                    UUID scene = UUID.randomUUID();
                    if (i == size / 2) changedScene = scene;
                    statement.setBytes(1, bytes(manuscriptId)); statement.setBytes(2, bytes(scene));
                    statement.setString(3, "场景" + i + " 中文正文 ".repeat(200)); statement.setLong(4, 1000);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
            Manuscript working = new Manuscript(); working.setId(manuscriptId); working.setContentStorageVersion(2);
            long started = System.nanoTime();
            content.writeScene(working, changedScene, "<p>修订😀</p>");
            long elapsed = Duration.ofNanos(System.nanoTime() - started).toMillis();
            assertEquals("<p>修订😀</p>", content.readScene(working, changedScene));
            assertEquals(size, jdbc.queryForObject("select count(*) from manuscript_scene_contents where manuscript_id=?", Integer.class, bytes(manuscriptId)));
            assertEquals("{}", jdbc.queryForObject("select sections_json from manuscripts where id=?", String.class, bytes(manuscriptId)));
            String key = String.valueOf(jdbc.queryForList("explain select content from manuscript_scene_contents where manuscript_id=? and scene_id=?",
                    bytes(manuscriptId), bytes(changedScene)).get(0).get("key"));
            assertEquals("PRIMARY", key);
            System.out.printf("CAPACITY scenes=%d singleSceneWriteMs=%d archiveBytes=2 sqlKey=%s heapMiB=%d%n",
                    size, elapsed, key, usedHeapMiB());
        }

        var adminAssets = new AdminOperationsReadRepository(new NamedParameterJdbcTemplate(ds));
        for (int target : List.of(1000, 10000)) {
            int from = target == 1000 ? 0 : 1000;
            long started = System.nanoTime();
            try (Connection connection = ds.getConnection(); PreparedStatement stories = connection.prepareStatement(
                    "insert into stories(id,title,status,synopsis,updated_at) values(?,?,?, ?,CURRENT_TIMESTAMP)")) {
                for (int i = from; i < target; i++) {
                    stories.setBytes(1, bytes(UUID.randomUUID())); stories.setString(2, "Synthetic story " + i);
                    stories.setString(3, "active"); stories.setString(4, "正文".repeat(2048)); stories.addBatch();
                    if (i % 500 == 499) stories.executeBatch();
                }
                stories.executeBatch();
            }
            var page = adminAssets.assets("stories", 0, 20, "");
            assertEquals(target, page.total()); assertEquals(20, page.items().size());
            int responseBytes = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(page).length;
            assertTrue(responseBytes < 8192, "Admin asset page grew with the synopsis LOB");
            String key = String.valueOf(jdbc.queryForList(
                    "explain select id,title,status,updated_at from stories order by updated_at desc,id desc limit 20"
                    ).get(0).get("key"));
            assertEquals("idx_admin_stories_page", key);
            System.out.printf("CAPACITY assets=%d insertMs=%d page=20 responseBytes=%d sqlKey=%s heapMiB=%d%n",
                    target, Duration.ofNanos(System.nanoTime() - started).toMillis(), responseBytes, key, usedHeapMiB());
        }

        final String term = "0000000000000001";
        for (int target : List.of(100, 1000, 10000)) {
            int from = target == 100 ? 0 : target == 1000 ? 100 : 1000;
            long started = System.nanoTime();
            try (Connection connection = ds.getConnection();
                 PreparedStatement materials = connection.prepareStatement("insert into materials(id,title,type,summary,content,status,source,content_version,created_at) values(?,?,?,? ,?,'pending','UPLOAD',1,CURRENT_TIMESTAMP)");
                 PreparedStatement fingerprints = connection.prepareStatement("insert into material_duplicate_fingerprints(material_id,content_version,title,terms_json) values(?,1,?,?)");
                 PreparedStatement terms = connection.prepareStatement("insert into material_duplicate_terms(term,material_id,content_version) values(?,?,1)")) {
                for (int i = from; i < target; i++) {
                    byte[] id = bytes(UUID.randomUUID());
                    String title = "Synthetic " + i;
                    materials.setBytes(1,id); materials.setString(2,title); materials.setString(3,"text");
                    materials.setString(4,"Synthetic summary"); materials.setString(5,"正文".repeat(2048)); materials.addBatch();
                    fingerprints.setBytes(1,id); fingerprints.setString(2,title); fingerprints.setString(3,"[\"" + term + "\"]"); fingerprints.addBatch();
                    terms.setString(1,term); terms.setBytes(2,id); terms.addBatch();
                    if (i % 500 == 499) { materials.executeBatch(); fingerprints.executeBatch(); terms.executeBatch(); }
                }
                materials.executeBatch(); fingerprints.executeBatch(); terms.executeBatch();
            }
            MaterialAdminReadRepository.Page page = new MaterialAdminReadRepository(new NamedParameterJdbcTemplate(ds)).pending(0, 20);
            assertEquals(target, page.totalElements()); assertEquals(20, page.items().size());
            int responseBytes = new ObjectMapper().findAndRegisterModules().writeValueAsBytes(page).length;
            assertTrue(responseBytes < 32768, "Admin page response grew with the full material body");
            List<byte[]> candidates = jdbc.query("select material_id from material_duplicate_terms where term=? order by material_id limit 51",
                    (rs, row) -> rs.getBytes(1), term);
            assertEquals(51, candidates.size());
            System.out.printf("CAPACITY materials=%d insertMs=%d pendingPage=20 responseBytes=%d candidateRecall=%d heapMiB=%d%n",
                    target, Duration.ofNanos(System.nanoTime() - started).toMillis(), responseBytes, candidates.size(), usedHeapMiB());
        }

        var duplicates = new MaterialDuplicateService(jdbc, mock(MaterialRepository.class),
                new MaterialFingerprintService(jdbc, new JsonColumnCodec(new ObjectMapper())),
                new TransactionTemplate(new DataSourceTransactionManager(ds)), 50, 100);
        try {
            MaterialDuplicateService.Job started = duplicates.start();
            duplicates.dispatch();
            Instant deadline = Instant.now().plusSeconds(60);
            MaterialDuplicateService.Job completed = duplicates.status(started.id());
            while (List.of("queued", "running").contains(completed.status()) && Instant.now().isBefore(deadline)) {
                Thread.sleep(100);
                completed = duplicates.status(started.id());
            }
            assertEquals("completed", completed.status(), "duplicate job: " + completed);
            assertEquals(100, completed.comparisons());
            assertTrue(completed.incomplete());
            assertTrue(duplicates.results(started.id(), 0, 20).total() <= 100);
            System.out.printf("CAPACITY duplicateInputs=10000 compared=%d incomplete=%s%n",
                    completed.comparisons(), completed.incomplete());
            System.out.printf("CAPACITY connectionsBefore=%d connectionsAfter=%d rowLockWaitMsDelta=%d%n",
                    startConnections, globalStatus(jdbc, "Threads_connected"),
                    globalStatus(jdbc, "Innodb_row_lock_time") - startLockWaitMillis);
        } finally { duplicates.close(); }
    }

    private static long globalStatus(JdbcTemplate jdbc, String name) {
        return jdbc.query("show global status like '" + name + "'", (rs, row) -> Long.parseLong(rs.getString(2)))
                .stream().findFirst().orElse(0L);
    }

    private static long usedHeapMiB() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }
}
