package com.ainovel.app.manuscript;

import com.ainovel.app.manuscript.model.Manuscript;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.ainovel.app.common.JsonColumnCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;

/** Opt-in isolated MySQL exercise: never accepts an existing business schema name. */
@EnabledIfEnvironmentVariable(named = "AIENIE_AUDIT_MYSQL_URL", matches = "jdbc:mysql:.*")
class ManuscriptStorageExternalMysqlTest {
    @Test void backfillResumeCorruptionBlockReverseAndScale() throws Exception {
        String url = System.getenv("AIENIE_AUDIT_MYSQL_URL");
        assertTrue(url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"));
        String user = Objects.requireNonNull(System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"));
        String password = Objects.requireNonNull(System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD"));
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        try (Connection c = DriverManager.getConnection(url,user,password)) {
            UUID first = UUID.randomUUID(), second = UUID.randomUUID(), invalid = UUID.randomUUID();
            UUID sceneA = UUID.randomUUID(), sceneB = UUID.randomUUID();
            String original = "{\"" + sceneA + "\":\"<p>中文😀</p>\",\"" + sceneB + "\":\"<p>second</p>\"}";
            insert(c,first,original); insert(c,second,"{}");
            assertEquals(1, ManuscriptStorageMaintenance.run(c,"backfill",1));
            assertEquals(2, scalar(c,"select count(*) from manuscripts where content_storage_version=1 and id in (?,?)",bytes(first),bytes(second)) + 1);
            assertEquals(1, ManuscriptStorageMaintenance.run(c,"backfill",1));
            assertEquals(2, ManuscriptStorageMaintenance.run(c,"verify",100));
            execute(c,"update manuscript_content_migration set content_hash='tampered' where manuscript_id=?",bytes(first));
            assertThrows(IllegalStateException.class, () -> ManuscriptStorageMaintenance.run(c,"verify",100));
            assertEquals(0, ManuscriptStorageMaintenance.run(c,"backfill",1));
            assertEquals(2, ManuscriptStorageMaintenance.run(c,"verify",100));
            assertEquals(original, one(c,"select sections_json from manuscripts where id=?",bytes(first)));
            insert(c,invalid,"{bad-json");
            assertThrows(RuntimeException.class, () -> ManuscriptStorageMaintenance.run(c,"backfill",100));
            assertEquals(1, scalar(c,"select content_storage_version from manuscripts where id=?",bytes(invalid)));
            assertEquals(0, scalar(c,"select count(*) from manuscript_scene_contents where manuscript_id=?",bytes(invalid)));
            execute(c,"update manuscripts set sections_json='{}' where id=?",bytes(invalid));
            assertEquals(1, ManuscriptStorageMaintenance.run(c,"backfill",100));
            // Fixed-scene edit changes only its row; the original archive remains byte-for-byte intact.
            DriverManagerDataSource ds = new DriverManagerDataSource(url,user,password);
            ManuscriptContentService contents = new ManuscriptContentService(new JdbcTemplate(ds),new JsonColumnCodec(new ObjectMapper()),true);
            Manuscript working = new Manuscript(); working.setId(first); working.setContentStorageVersion(2);
            assertEquals("<p>中文😀</p>", contents.readScene(working,sceneA));
            contents.writeScene(working,sceneA,"<p>修订😀</p>");
            assertEquals("<p>second</p>", contents.readScene(working,sceneB));
            assertEquals(original, one(c,"select sections_json from manuscripts where id=?",bytes(first)));
            assertEquals(3, ManuscriptStorageMaintenance.run(c,"reverse",100));
            assertEquals(1, scalar(c,"select content_storage_version from manuscripts where id=?",bytes(first)));
            assertEquals("<p>修订😀</p>",new JsonColumnCodec(new ObjectMapper()).readSections(one(c,"select sections_json from manuscripts where id=?",bytes(first))).get(sceneA.toString()));
            // Immutable version/snapshot tables are never updated by maintenance.
            assertEquals(0, scalar(c,"select count(*) from manuscript_content_migration where manuscript_id=? and scene_count<>2",bytes(first)));
        }
    }
    private static void insert(Connection c,UUID id,String body) throws Exception {
        execute(c,"insert into manuscripts(id,sections_json,content_storage_version,version) values(?,?,1,0)",bytes(id),body);
    }
    private static void execute(Connection c,String sql,Object...args) throws Exception { try(var p=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);p.executeUpdate(); } }
    private static int scalar(Connection c,String sql,Object...args) throws Exception { try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){r.next();return r.getInt(1);}} }
    private static String one(Connection c,String sql,Object...args) throws Exception { try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){r.next();return r.getString(1);}} }
}
