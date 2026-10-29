package com.ainovel.app.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "externalMysql.enabled", matches = "true")
class ExternalMySqlMigrationVerificationTest {
    private static final String DATABASE_PREFIX = "ainovel_verify_";
    private static final Pattern DATABASE_NAME_PATTERN =
            Pattern.compile("^" + DATABASE_PREFIX + "[0-9a-f]{32}$");
    private static final Pattern SAFE_HOST_PATTERN = Pattern.compile("^[A-Za-z0-9._:-]+$");
    private static final String JDBC_OPTIONS =
            "useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
                    + "&allowPublicKeyRetrieval=true&useSSL=false"
                    + "&connectTimeout=10000&socketTimeout=60000";

    @Test
    void migratesFreshExternalDatabaseThroughLatest() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            var migration = flyway(config, databaseUrl);
            int pending = migration.info().pending().length;
            var result = migration.migrate();

            assertEquals(pending, result.migrationsExecuted);
            SceneGenerationSchemaAssertions.assertLatestSchema(
                    databaseUrl, config.username, config.password, databaseName);
            assertNarrativeSchema(config, databaseUrl);
            assertAssistantSchema(config, databaseUrl);
            assertStoryAssistantSchema(config, databaseUrl);
            assertModelConfigurationSchema(config,databaseUrl);
            assertEquals(0, flyway(config, databaseUrl).migrate().migrationsExecuted);
        });
    }

    @Test void upgradesV38PreservingLegacyModelsPreferencesAndCreditLedger() throws Exception {
        ExternalMySqlConfig config=ExternalMySqlConfig.load();
        withIsolatedDatabase(config,(name,url)->{
            Flyway.configure().dataSource(url,config.username,config.password).locations("classpath:db/migration").target("38").load().migrate();
            String user=UUID.randomUUID().toString().replace("-",""),model=UUID.randomUUID().toString().replace("-","");
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                q.executeUpdate("INSERT INTO users(id,username,email,password_hash,credits,banned) VALUES(UNHEX('"+user+"'),'migration-model-user','migration-model@example.invalid','fixture',87,false)");
                q.executeUpdate("INSERT INTO model_registry(id,model_key,display_name,provider,max_context_tokens,max_output_tokens,cost_per_1k_input,cost_per_1k_output,supports_streaming,is_available,priority) VALUES(UNHEX('"+model+"'),'legacy-model','Legacy','fixture',64000,8192,0.01,0.02,true,true,100)");
                q.executeUpdate("INSERT INTO task_model_routing(id,task_type,recommended_model_id,fallback_model_id,routing_strategy,config_json) VALUES(UNHEX(REPLACE(UUID(),'-','')),'draft_generation',UNHEX('"+model+"'),UNHEX('"+model+"'),'fixed','{\"legacy\":true}')");
                q.executeUpdate("INSERT INTO user_model_preferences(id,user_id,task_type,preferred_model_id) VALUES(UNHEX(REPLACE(UUID(),'-','')),UNHEX('"+user+"'),'draft_generation',UNHEX('"+model+"'))");
                q.executeUpdate("INSERT INTO project_credit_ledger(id,user_id,entry_type,delta,balance_after,reference_type,reference_id,description,idempotency_key) VALUES(UNHEX(REPLACE(UUID(),'-','')),UNHEX('"+user+"'),'AI_DEBIT',-13,87,'AI_USAGE','legacy','Historical charge','legacy-charge')");
            }
            assertEquals(3,flyway(config,url).migrate().migrationsExecuted);
            assertModelConfigurationSchema(config,url);
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                try(var rows=q.executeQuery("select credits from users where id=UNHEX('"+user+"')")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals(87,rows.getLong(1));}
                try(var rows=q.executeQuery("select p.task_type,hex(p.preferred_model_id),p.preferred_option_id,m.model_key,m.thinking_json from user_model_preferences p join model_registry m on p.preferred_model_id=m.id where p.user_id=UNHEX('"+user+"')")) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals("draft_generation",rows.getString(1));assertEquals(model.toUpperCase(),rows.getString(2));org.junit.jupiter.api.Assertions.assertNull(rows.getString(3));assertEquals("legacy-model",rows.getString(4));org.junit.jupiter.api.Assertions.assertNull(rows.getString(5));
                }
                try(var rows=q.executeQuery("select delta,balance_after,description from project_credit_ledger where user_id=UNHEX('"+user+"')")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals(-13,rows.getLong(1));assertEquals(87,rows.getLong(2));assertEquals("Historical charge",rows.getString(3));org.junit.jupiter.api.Assertions.assertFalse(rows.next());}
                try(var rows=q.executeQuery("select config_json from task_model_routing where task_type='draft_generation'")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals("{\"legacy\":true}",rows.getString(1));}
            }
            assertEquals(0,flyway(config,url).migrate().migrationsExecuted);
        });
    }
    @Test void upgradesV39WithoutInventingSlopProvenanceOrRepricingHistory() throws Exception {
        var config=ExternalMySqlConfig.load();
        withIsolatedDatabase(config,(name,url)->{
            Flyway.configure().dataSource(url,config.username,config.password).locations("classpath:db/migration").target("39").load().migrate();
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                q.execute("INSERT INTO stories(id,title) VALUES(UNHEX(REPEAT('03',16)),'历史 Slop 作品')");
                q.execute("INSERT INTO manuscripts(id,sections_json,version) VALUES(UNHEX(REPEAT('01',16)),'{}',0)");
                q.execute("INSERT INTO slop_quality_runs(id,story_id,manuscript_id,scene_id,status,max_severity,overall_risk_score,revised,summary) VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('03',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('04',16)),'ACCEPTED','LOW',12,0,'历史报告😀')");
                q.execute("INSERT INTO language_quality_reports(id,manuscript_id,scene_id,source_key,data_json,created_at) VALUES(UNHEX(REPEAT('05',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('04',16)),'old-report','{\"status\":\"ISSUES\"}',NOW(6))");
                q.execute("INSERT INTO language_quality_patches(id,report_id,issue_id,data_json,created_at) VALUES(UNHEX(REPEAT('06',16)),UNHEX(REPEAT('05',16)),'old-issue','{\"candidate\":\"原候选😀\"}',NOW(6))");
                q.execute("INSERT INTO ai_validation_budgets(id,used,call_limit) VALUES('historical',1,6)");
                q.execute("INSERT INTO ai_validation_calls(id,run_id,attempt,request_id,request_json,status,created_at) VALUES(UNHEX(REPEAT('07',16)),'historical',1,'original-id','{}','STARTED',NOW(6))");
            }
            assertEquals(2,flyway(config,url).migrate().migrationsExecuted);
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                try(var r=q.executeQuery("SELECT summary,review_context_json FROM slop_quality_runs")) {
                    org.junit.jupiter.api.Assertions.assertTrue(r.next());assertEquals("历史报告😀",r.getString(1));org.junit.jupiter.api.Assertions.assertNull(r.getString(2));
                }
                try(var r=q.executeQuery("SELECT data_json FROM language_quality_patches")) {
                    org.junit.jupiter.api.Assertions.assertTrue(r.next());assertEquals("{\"candidate\":\"原候选😀\"}",r.getString(1));
                }
                try(var r=q.executeQuery("SELECT request_id,status,budget_id,settled_cost,settlement_state FROM ai_validation_calls")) {
                    org.junit.jupiter.api.Assertions.assertTrue(r.next());assertEquals("original-id",r.getString(1));assertEquals("STARTED",r.getString(2));
                    org.junit.jupiter.api.Assertions.assertNull(r.getString(3));org.junit.jupiter.api.Assertions.assertNull(r.getBigDecimal(4));org.junit.jupiter.api.Assertions.assertNull(r.getString(5));
                }
            }
            assertEquals(0,flyway(config,url).migrate().migrationsExecuted);
        });
    }
    private static void assertModelConfigurationSchema(ExternalMySqlConfig config,String url) throws Exception {
        try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
            try(var rows=q.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('ai_model_options','ai_model_catalog_state','ai_model_quotes')")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals(3,rows.getInt(1));}
            try(var rows=q.executeQuery("select revision,last_success_at from ai_model_catalog_state where id=1")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals(0,rows.getLong(1));org.junit.jupiter.api.Assertions.assertNull(rows.getTimestamp(2));}
            try(var rows=q.executeQuery("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_credit_reservations' AND COLUMN_NAME='frozen_request_json'")){org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals("longtext",rows.getString(1));}
        }
    }

    @Test
    void upgradesV36PreservingCandidatesAndEnforcingAssistantIdentity() throws Exception {
        var config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (name, url) -> {
            Flyway.configure().dataSource(url, config.username, config.password)
                    .locations("classpath:db/migration").target("36").load().migrate();
            try (var connection = DriverManager.getConnection(url, config.username, config.password);
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO manuscripts(id,sections_json,version) VALUES(UNHEX(REPEAT('01',16)),'{}',0)");
                statement.execute("INSERT INTO manuscript_branches(id,manuscript_id,name,status,is_main) VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('01',16)),'main','active',1)");
                statement.execute("INSERT INTO narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at) VALUES(UNHEX(REPEAT('03',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('02',16)),UNHEX(REPEAT('04',16)),'<p>兼容旧候选😀</p>','{}','LENGTH_REJECTED',NOW(6))");
            }
            assertEquals(5, flyway(config, url).migrate().migrationsExecuted);
            assertAssistantSchema(config, url);
            try (var connection = DriverManager.getConnection(url, config.username, config.password);
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT content,operation_id FROM narrative_generation_candidates")) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                    assertEquals("<p>兼容旧候选😀</p>", rows.getString(1));
                    org.junit.jupiter.api.Assertions.assertNull(rows.getBytes(2));
                }
                statement.execute("INSERT INTO writing_assistant_sessions(id,user_id,manuscript_id,branch_id,scene_id,created_at) VALUES(UNHEX(REPEAT('05',16)),UNHEX(REPEAT('06',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('02',16)),UNHEX(REPEAT('04',16)),NOW(6))");
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO writing_assistant_sessions SELECT UNHEX(REPEAT('07',16)),user_id,manuscript_id,branch_id,scene_id,created_at FROM writing_assistant_sessions"));
                statement.execute("INSERT INTO writing_assistant_turns(id,session_id,request_key,request_hash,operation_id,frozen_json,created_at) VALUES(UNHEX(REPEAT('08',16)),UNHEX(REPEAT('05',16)),'request-1','hash',UNHEX(REPEAT('09',16)),'{\"draft\":\"原稿😀\"}',NOW(6))");
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO writing_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) SELECT UNHEX(REPEAT('10',16)),session_id,request_key,request_hash,frozen_json,created_at FROM writing_assistant_turns"));
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO writing_assistant_turns(id,session_id,request_key,request_hash,operation_id,frozen_json,created_at) SELECT UNHEX(REPEAT('10',16)),session_id,'request-2',request_hash,operation_id,frozen_json,created_at FROM writing_assistant_turns"));
                statement.execute("INSERT INTO writing_assistant_proposals(id,turn_id,status,data_json,receipts_json,created_at) VALUES(UNHEX(REPEAT('11',16)),UNHEX(REPEAT('08',16)),'READY','{}','{}',NOW(6))");
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO writing_assistant_proposals SELECT UNHEX(REPEAT('12',16)),turn_id,status,data_json,receipts_json,created_at FROM writing_assistant_proposals"));
            }
            assertEquals(0, flyway(config, url).migrate().migrationsExecuted);
        });
    }

    private static void assertAssistantSchema(ExternalMySqlConfig config, String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, config.username, config.password);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('writing_assistant_sessions','writing_assistant_turns','writing_assistant_proposals')")) {
            org.junit.jupiter.api.Assertions.assertTrue(rows.next());
            assertEquals(3, rows.getInt(1));
        }
    }

    @Test
    void upgradesV37WithoutChangingCharactersOrFrozenSceneConversations() throws Exception {
        var config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (name, url) -> {
            Flyway.configure().dataSource(url, config.username, config.password)
                    .locations("classpath:db/migration").target("37").load().migrate();
            try (var connection = DriverManager.getConnection(url, config.username, config.password);
                 var statement = connection.createStatement()) {
                statement.execute("INSERT INTO stories(id,title) VALUES(UNHEX(REPEAT('01',16)),'旧故事')");
                statement.execute("INSERT INTO character_cards(id,story_id,name,details,relationships) VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('01',16)),'林青','旧设定😀','妹妹')");
                statement.execute("INSERT INTO manuscripts(id,sections_json,version) VALUES(UNHEX(REPEAT('03',16)),'{}',0)");
                statement.execute("INSERT INTO writing_assistant_sessions(id,user_id,manuscript_id,branch_id,scene_id,created_at) VALUES(UNHEX(REPEAT('04',16)),UNHEX(REPEAT('05',16)),UNHEX(REPEAT('03',16)),UNHEX(REPEAT('06',16)),UNHEX(REPEAT('07',16)),NOW(6))");
                statement.execute("INSERT INTO writing_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) VALUES(UNHEX(REPEAT('08',16)),UNHEX(REPEAT('04',16)),'old-request','old-hash','{\"original\":\"冻结正文😀\"}',NOW(6))");
            }
            assertEquals(4, flyway(config, url).migrate().migrationsExecuted);
            assertStoryAssistantSchema(config, url);
            try (var connection = DriverManager.getConnection(url, config.username, config.password);
                 var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT details,relationships,revision FROM character_cards")) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                    assertEquals("旧设定😀", rows.getString(1));
                    assertEquals("妹妹", rows.getString(2));
                    assertEquals(0, rows.getLong(3));
                }
                try (var rows = statement.executeQuery("SELECT request_key,frozen_json FROM writing_assistant_turns")) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                    assertEquals("old-request", rows.getString(1));
                    assertEquals("{\"original\":\"冻结正文😀\"}", rows.getString(2));
                }
                statement.execute("INSERT INTO story_assistant_sessions(id,story_id,user_id,title,created_at,updated_at) VALUES(UNHEX(REPEAT('10',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('05',16)),'人物讨论',NOW(6),NOW(6))");
                statement.execute("INSERT INTO story_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) VALUES(UNHEX(REPEAT('11',16)),UNHEX(REPEAT('10',16)),'new-request','new-hash','{}',NOW(6))");
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () -> statement.execute("INSERT INTO story_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) SELECT UNHEX(REPEAT('12',16)),session_id,request_key,request_hash,frozen_json,created_at FROM story_assistant_turns"));
            }
            assertEquals(0, flyway(config, url).migrate().migrationsExecuted);
        });
    }

    private static void assertStoryAssistantSchema(ExternalMySqlConfig config, String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, config.username, config.password);
             var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('story_assistant_sessions','story_assistant_turns','story_assistant_proposals','story_assistant_receipts')")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                assertEquals(4, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT COLUMN_DEFAULT,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='character_cards' AND COLUMN_NAME='revision'")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                assertEquals("0", rows.getString(1));
                assertEquals("NO", rows.getString(2));
            }
        }
    }

    @Test
    void upgradesV35PreservingH2CandidatesAndEnforcingOperationSequence() throws Exception {
        var config=ExternalMySqlConfig.load();
        withIsolatedDatabase(config,(name,url)->{
            Flyway.configure().dataSource(url,config.username,config.password).locations("classpath:db/migration").target("35").load().migrate();
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                q.execute("INSERT INTO manuscripts(id,sections_json,version) VALUES(UNHEX(REPEAT('01',16)),'{}',0)");
                q.execute("INSERT INTO manuscript_branches(id,manuscript_id,name,status,is_main) VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('01',16)),'main','active',1)");
                q.execute("INSERT INTO narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at) VALUES(UNHEX(REPEAT('03',16)),UNHEX(REPEAT('01',16)),UNHEX(REPEAT('02',16)),UNHEX(REPEAT('04',16)),'<p>旧 H2 正文</p>','{}','LENGTH_REJECTED',NOW(6))");
            }
            assertEquals(6,flyway(config,url).migrate().migrationsExecuted);
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                try(var rows=q.executeQuery("SELECT content,operation_id FROM narrative_generation_candidates")) {
                    org.junit.jupiter.api.Assertions.assertTrue(rows.next());assertEquals("<p>旧 H2 正文</p>",rows.getString(1));org.junit.jupiter.api.Assertions.assertNull(rows.getBytes(2));
                }
                q.execute("UPDATE narrative_generation_candidates SET operation_id=UNHEX(REPEAT('05',16)),generation_sequence=1");
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class,()->q.execute("INSERT INTO narrative_generation_candidates(id,manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence) SELECT UNHEX(REPEAT('06',16)),manuscript_id,branch_id,scene_id,content,stamp_json,status,created_at,operation_id,generation_sequence FROM narrative_generation_candidates"));
            }
            assertEquals(0,flyway(config,url).migrate().migrationsExecuted);
        });
    }

    @Test
    void upgradesExternalDatabaseFromV14ThroughLatest() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            var v14Result = Flyway.configure()
                    .dataSource(databaseUrl, config.username, config.password)
                    .locations("classpath:db/migration")
                    .target("14")
                    .load()
                    .migrate();
            assertEquals(14, v14Result.migrationsExecuted);

            var migration = flyway(config, databaseUrl);
            int pending = migration.info().pending().length;
            var latestResult = migration.migrate();
            assertEquals(pending, latestResult.migrationsExecuted);
            SceneGenerationSchemaAssertions.assertLatestSchema(
                    databaseUrl, config.username, config.password, databaseName);
            assertNarrativeSchema(config, databaseUrl);
            assertEquals(0, flyway(config, databaseUrl).migrate().migrationsExecuted);
        });
    }

    @Test
    void upgradesStagingV23ThroughLatestAndIsIdempotent() throws Exception {
        assertUpgradeThroughLatest("23", 18);
    }

    @Test
    void upgradesDevelopV33ThroughLatestAndIsIdempotent() throws Exception {
        assertUpgradeThroughLatest("33", 8);
    }

    private void assertUpgradeThroughLatest(String baseline, int expectedPending) throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            Flyway.configure().dataSource(databaseUrl, config.username, config.password)
                    .locations("classpath:db/migration").target(baseline).load().migrate();
            var migration = flyway(config, databaseUrl);
            assertEquals(expectedPending, migration.info().pending().length);
            assertEquals(expectedPending, migration.migrate().migrationsExecuted);
            assertEquals("41", migration.info().current().getVersion().getVersion());
            try (Connection connection = DriverManager.getConnection(databaseUrl, config.username, config.password);
                 Statement statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM admin_emergency_subjects WHERE subject_id='configured-admin'")) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
            }
            assertEquals(0, flyway(config, databaseUrl).migrate().migrationsExecuted);
        });
    }

    @Test
    void restoresLegacyTinytextCapacityWithoutLosingDataOrCollation() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (databaseName, databaseUrl) -> {
            Flyway.configure().dataSource(databaseUrl, config.username, config.password)
                    .locations("classpath:db/migration").target("34").load().migrate();
            String before = "{\"text\":\"旧稿😀\"}";
            try (Connection connection = DriverManager.getConnection(databaseUrl, config.username, config.password);
                 Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE ai_operation_runs MODIFY payload_json TINYTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL");
                statement.execute("SET FOREIGN_KEY_CHECKS=0");
                try (var insert = connection.prepareStatement("INSERT INTO ai_operation_runs(id,user_id,operation_type,status,payload_json) VALUES(UNHEX(REPEAT('01',16)),UNHEX(REPEAT('02',16)),'TEST','SUCCEEDED',?)")) {
                    insert.setString(1, before);
                    insert.executeUpdate();
                }
                statement.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            assertEquals(7, flyway(config, databaseUrl).migrate().migrationsExecuted);
            try (Connection connection = DriverManager.getConnection(databaseUrl, config.username, config.password);
                 Statement statement = connection.createStatement()) {
                try (var columns = statement.executeQuery("SELECT DATA_TYPE,COLLATION_NAME,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='ai_operation_runs' AND COLUMN_NAME='payload_json'")) {
                    org.junit.jupiter.api.Assertions.assertTrue(columns.next());
                    assertEquals("longtext", columns.getString(1));
                    assertEquals("utf8mb4_bin", columns.getString(2));
                    assertEquals("YES", columns.getString(3));
                }
                try (var row = statement.executeQuery("SELECT payload_json FROM ai_operation_runs")) {
                    org.junit.jupiter.api.Assertions.assertTrue(row.next());
                    assertEquals(before, row.getString(1));
                }
                String longPayload = "正文😀".repeat(1000);
                try (var update = connection.prepareStatement("UPDATE ai_operation_runs SET payload_json=?")) {
                    update.setString(1, longPayload);
                    assertEquals(1, update.executeUpdate());
                }
                try (var row = statement.executeQuery("SELECT payload_json FROM ai_operation_runs")) {
                    org.junit.jupiter.api.Assertions.assertTrue(row.next());
                    assertEquals(longPayload, row.getString(1));
                }
            }
            assertEquals(0, flyway(config, databaseUrl).migrate().migrationsExecuted);
        });
    }

    @Test
    void upgradesV18ToLatestWithoutChangingArchivedManuscriptBody() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (name, url) -> {
            Flyway.configure().dataSource(url, config.username, config.password)
                    .locations("classpath:db/migration").target("18").load().migrate();
            UUID manuscriptId = UUID.randomUUID();
            UUID sceneId = UUID.randomUUID();
            String body = "{\"" + sceneId + "\":\"<p>迁移前正文😀</p>\"}";
            try (Connection c = DriverManager.getConnection(url, config.username, config.password);
                 var insert = c.prepareStatement("insert into manuscripts(id,sections_json,version) values(?,?,0)")) {
                java.nio.ByteBuffer id = java.nio.ByteBuffer.allocate(16).putLong(manuscriptId.getMostSignificantBits()).putLong(manuscriptId.getLeastSignificantBits());
                insert.setBytes(1,id.array()); insert.setString(2,body); insert.executeUpdate();
            }
            var migration = flyway(config, url);
            int pending = migration.info().pending().length;
            assertEquals(pending,migration.migrate().migrationsExecuted);
            try (Connection c = DriverManager.getConnection(url, config.username, config.password);
                 var query = c.prepareStatement("select sections_json,content_storage_version from manuscripts where id=?")) {
                java.nio.ByteBuffer id = java.nio.ByteBuffer.allocate(16).putLong(manuscriptId.getMostSignificantBits()).putLong(manuscriptId.getLeastSignificantBits());
                query.setBytes(1,id.array());
                try (var row=query.executeQuery()) { org.junit.jupiter.api.Assertions.assertTrue(row.next()); assertEquals(body,row.getString(1)); assertEquals(1,row.getInt(2)); }
            }
            assertEquals(0,flyway(config,url).migrate().migrationsExecuted);
        });
    }

    @Test
    void upgradesLegacyLedgerWithoutLosingRowsOrAmounts() throws Exception {
        ExternalMySqlConfig config = ExternalMySqlConfig.load();
        withIsolatedDatabase(config, (name, url) -> {
            Flyway.configure().dataSource(url, config.username, config.password)
                    .locations("classpath:db/migration").target("15").load().migrate();
            try (var c = DriverManager.getConnection(url, config.username, config.password); var q = c.createStatement()) {
                q.execute("ALTER TABLE project_credit_ledger MODIFY entry_type ENUM('ADMIN_GRANT','AI_DEBIT','CHECKIN','CONVERT_IN','REDEEM_CODE') NOT NULL");
                q.execute("SET FOREIGN_KEY_CHECKS=0");
                String[] legacyTypes = {"ADMIN_GRANT", "AI_DEBIT", "CHECKIN", "CONVERT_IN", "REDEEM_CODE"};
                for (int index = 0; index < legacyTypes.length; index++) {
                    q.execute("INSERT INTO project_credit_ledger (id,user_id,delta,balance_after,entry_type,created_at) VALUES (UNHEX(LPAD('"
                            + (index + 1) + "',32,'0')),UNHEX(REPEAT('02',16))," + (index == 1 ? -17 : 123 + index)
                            + "," + (456 + index) + ",'" + legacyTypes[index] + "',NOW())");
                }
                q.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            var migration = flyway(config, url);
            int pending = migration.info().pending().length;
            assertEquals(pending, migration.migrate().migrationsExecuted);
            assertEquals(0, flyway(config, url).migrate().migrationsExecuted);
            try (var c = DriverManager.getConnection(url, config.username, config.password); var q = c.createStatement()) {
                String[] legacyTypes = {"ADMIN_GRANT", "AI_DEBIT", "CHECKIN", "CONVERT_IN", "REDEEM_CODE"};
                try (var r = q.executeQuery("SELECT entry_type,delta,balance_after FROM project_credit_ledger ORDER BY id")) {
                    for (int index = 0; index < legacyTypes.length; index++) {
                        org.junit.jupiter.api.Assertions.assertTrue(r.next());
                        assertEquals(legacyTypes[index], r.getString(1));
                        assertEquals(index == 1 ? -17 : 123 + index, r.getInt(2));
                        assertEquals(456 + index, r.getInt(3));
                    }
                    org.junit.jupiter.api.Assertions.assertFalse(r.next());
                }
                for (var type : com.ainovel.app.economy.model.CreditLedgerType.values()) {
                    q.executeUpdate("UPDATE project_credit_ledger SET entry_type='" + type.name() + "'");
                }
                try (var r = q.executeQuery("SELECT COLUMN_TYPE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='project_credit_ledger' AND column_name='entry_type'")) {
                    r.next(); assertEquals("varchar(32)", r.getString(1));
                }
            }
        });
    }

    @Test void upgradesV17PreservingHistoricalNarrativeAndKeepingOldWorksDisabled() throws Exception {
        var config=ExternalMySqlConfig.load();
        withIsolatedDatabase(config,(name,url)->{
            Flyway.configure().dataSource(url,config.username,config.password).locations("classpath:db/migration").target("17").load().migrate();
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                q.execute("INSERT INTO stories(id,title) VALUES(UNHEX(REPEAT('01',16)),'旧作品𠮷😀')");
                q.execute("SET FOREIGN_KEY_CHECKS=0");
                q.execute("INSERT INTO narrative_records(id,extraction_id,assertion_json,dependency_ids_json,created_revision,created_at) VALUES(UNHEX(REPEAT('02',16)),UNHEX(REPEAT('03',16)),'{\"kind\":\"UTTERANCE\",\"statement\":\"旧证据𠮷😀\"}','[]',7,NOW())");
                q.execute("SET FOREIGN_KEY_CHECKS=1");
            }
            var migration = flyway(config, url);
            int pending = migration.info().pending().length;
            assertEquals(pending, migration.migrate().migrationsExecuted);
            assertNarrativeSchema(config,url);
            try(var c=DriverManager.getConnection(url,config.username,config.password);var q=c.createStatement()) {
                try(var r=q.executeQuery("SELECT assertion_json,dependency_ids_json,created_revision FROM narrative_records")) {
                    org.junit.jupiter.api.Assertions.assertTrue(r.next()); assertEquals("{\"kind\":\"UTTERANCE\",\"statement\":\"旧证据𠮷😀\"}",r.getString(1));
                    assertEquals("[]",r.getString(2));assertEquals(7,r.getInt(3));org.junit.jupiter.api.Assertions.assertFalse(r.next());
                }
                try(var r=q.executeQuery("SELECT COUNT(*) FROM narrative_context_settings")) {r.next();assertEquals(0,r.getInt(1));}
                q.execute("INSERT INTO narrative_context_settings(story_id) VALUES(UNHEX(REPEAT('01',16)))");
                try(var r=q.executeQuery("SELECT enabled,revision FROM narrative_context_settings")){r.next();assertEquals(false,r.getBoolean(1));assertEquals(0,r.getLong(2));}
            }
            assertEquals(0,flyway(config,url).migrate().migrationsExecuted);
        });
    }

    private static void assertNarrativeSchema(ExternalMySqlConfig config, String url) throws Exception {
        try (var connection = DriverManager.getConnection(url, config.username, config.password);
             var statement = connection.createStatement()) {
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('narrative_context_settings','narrative_context_revisions','narrative_generation_candidates','ai_validation_budgets','ai_validation_calls')")) {
                result.next(); assertEquals(5, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('narrative_ledgers','narrative_approvals','narrative_extractions','narrative_records','narrative_commits')")) {
                result.next(); assertEquals(5, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='character_cards' AND column_name IN ('role','archetype') AND is_nullable='YES'")) {
                result.next(); assertEquals(2, result.getInt(1));
            }
            try (var result = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='manuscript_versions' AND column_name='narrative_protected'")) {
                result.next(); assertEquals(1, result.getInt(1));
            }
        }
    }

    private static Flyway flyway(ExternalMySqlConfig config, String databaseUrl) {
        return Flyway.configure()
                .dataSource(databaseUrl, config.username, config.password)
                .locations("classpath:db/migration")
                .load();
    }

    private static void withIsolatedDatabase(ExternalMySqlConfig config,
                                             IsolatedDatabaseOperation operation) throws Exception {
        String databaseName = DATABASE_PREFIX + UUID.randomUUID().toString().replace("-", "");
        requireSafeDatabaseName(databaseName);
        boolean created = false;
        Throwable operationFailure = null;
        try {
            try (Connection connection = DriverManager.getConnection(
                    config.serverUrl(), config.username, config.password);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE DATABASE `" + databaseName
                        + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                created = true;
            }
            operation.run(databaseName, config.databaseUrl(databaseName));
        } catch (Exception | Error failure) {
            operationFailure = failure;
            throw failure;
        } finally {
            if (created) {
                try {
                    dropCreatedDatabase(config, databaseName);
                } catch (Exception | Error cleanupFailure) {
                    if (operationFailure != null) {
                        operationFailure.addSuppressed(cleanupFailure);
                    } else {
                        throw cleanupFailure;
                    }
                }
            }
        }
    }

    private static void dropCreatedDatabase(ExternalMySqlConfig config, String databaseName) throws Exception {
        requireSafeDatabaseName(databaseName);
        try (Connection connection = DriverManager.getConnection(config.serverUrl(), config.username, config.password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE `" + databaseName + "`");
        }
    }

    private static void requireSafeDatabaseName(String databaseName) {
        if (!DATABASE_NAME_PATTERN.matcher(databaseName).matches()) {
            throw new IllegalArgumentException("Refusing database operation for a non-verification database name");
        }
    }

    @FunctionalInterface
    private interface IsolatedDatabaseOperation {
        void run(String databaseName, String databaseUrl) throws Exception;
    }

    private static final class ExternalMySqlConfig {
        private final String host;
        private final int port;
        private final String username;
        private final String password;

        private ExternalMySqlConfig(String host, int port, String username, String password) {
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        private static ExternalMySqlConfig load() throws Exception {
            Path envFile = Path.of(requiredSystemProperty("externalMysql.envFile"))
                    .toAbsolutePath()
                    .normalize();
            if (!Files.isRegularFile(envFile, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(envFile)) {
                throw new IllegalStateException(
                        "externalMysql.envFile must point to a non-symlink regular file");
            }

            Map<String, String> values = parseEnvFile(envFile);
            String host = propertyOverride("externalMysql.host", values, "MYSQL_HOST");
            if (!SAFE_HOST_PATTERN.matcher(host).matches()) {
                throw new IllegalStateException("External MySQL host contains unsupported characters");
            }
            int port = parsePort(propertyOverride("externalMysql.port", values, "MYSQL_PORT"));
            String username = requiredEnvValue(values, "MYSQL_USER");
            String password = requiredEnvValue(values, "MYSQL_PASSWORD");
            return new ExternalMySqlConfig(host, port, username, password);
        }

        private String serverUrl() {
            return "jdbc:mysql://" + host + ":" + port + "/?" + JDBC_OPTIONS;
        }

        private String databaseUrl(String databaseName) {
            requireSafeDatabaseName(databaseName);
            return "jdbc:mysql://" + host + ":" + port + "/" + databaseName + "?" + JDBC_OPTIONS;
        }

        private static Map<String, String> parseEnvFile(Path envFile) throws Exception {
            Map<String, String> values = new LinkedHashMap<>();
            boolean acceptSection = true;
            boolean clientSection = false;
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    clientSection = "[client]".equals(trimmed);
                    acceptSection = clientSection;
                    continue;
                }
                if (!acceptSection) continue;
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring("export ".length()).trim();
                }
                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, separator).trim();
                String value = unquote(trimmed.substring(separator + 1).trim());
                if (clientSection) {
                    key = switch (key) {
                        case "host" -> "MYSQL_HOST";
                        case "port" -> "MYSQL_PORT";
                        case "user" -> "MYSQL_USER";
                        case "password" -> "MYSQL_PASSWORD";
                        default -> key;
                    };
                }
                values.put(key, value);
            }
            return values;
        }

        private static String requiredSystemProperty(String key) {
            String value = System.getProperty(key, "").trim();
            if (value.isEmpty()) {
                throw new IllegalStateException("Missing required system property: " + key);
            }
            return value;
        }

        private static String propertyOverride(String propertyName,
                                               Map<String, String> values,
                                               String envKey) {
            String propertyValue = System.getProperty(propertyName, "").trim();
            return propertyValue.isEmpty() ? requiredEnvValue(values, envKey) : propertyValue;
        }

        private static String requiredEnvValue(Map<String, String> values, String key) {
            String value = values.getOrDefault(key, "").trim();
            if (value.isEmpty() || value.startsWith("replace-")) {
                throw new IllegalStateException("Missing usable key in external MySQL env file: " + key);
            }
            return value;
        }

        private static int parsePort(String rawPort) {
            try {
                int parsed = Integer.parseInt(rawPort);
                if (parsed < 1 || parsed > 65535) {
                    throw new IllegalStateException("External MySQL port must be between 1 and 65535");
                }
                return parsed;
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("External MySQL port must be numeric", exception);
            }
        }

        private static String unquote(String value) {
            if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                return value.substring(1, value.length() - 1);
            }
            return value;
        }
    }
}
