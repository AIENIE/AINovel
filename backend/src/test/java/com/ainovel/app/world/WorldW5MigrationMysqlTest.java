package com.ainovel.app.world;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.util.*;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
class WorldW5MigrationMysqlTest {
    @Test void v47UpgradeDoesNotBackfillTimelineOrChangeSnapshotsTemplatesAndBindings() throws Exception {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL"),user=System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),password=System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        Flyway.configure().dataSource(url,user,password).target("47").load().migrate();
        try(var db=DriverManager.getConnection(url,user,password)) {
            UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),version=UUID.randomUUID(),story=UUID.randomUUID();
            var store=new WorldEntryStore(null,null,new ObjectMapper());
            String snapshot="{\"name\":\"旧世界\",\"tagline\":\"简介\",\"themes\":[],\"creativeIntent\":\"意图\",\"notes\":\"备注\",\"entries\":[],\"relations\":[],\"modules\":{},\"legacyModulesJson\":\"{\\\"未知字段\\\": [1, null]}\",\"overview\":\"历史概览\"}";
            String hash=store.hash(store.parse(snapshot));
            execute(db,"insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,0)",owner,"w5-upgrade","w5-upgrade@example.invalid");
            execute(db,"insert into worlds(id,user_id,name,status,version,revision,modules_json,themes_json,module_progress_json,draft_content_hash,legacy_modules_json,tagline,creative_intent,notes,overview) values(?,?,'旧世界','active','v1',7,'{}','[]','{}',?,'{\"未知字段\": [1, null]}','简介','意图','备注','历史概览')",world,owner,hash);
            execute(db,"insert into world_versions(id,world_id,sequence_number,origin,legacy_version,draft_revision,content_hash,snapshot_json,release_note,request_key,created_at) values(?,?,1,'PUBLISHED','v1',7,?,?,'原说明','old-publish',UTC_TIMESTAMP(6))",version,world,hash,snapshot);
            execute(db,"insert into stories(id,user_id,title,world_id,world_version_id,world_binding_revision) values(?,?,'多时代小说',?,?,4)",story,owner,world.toString(),version);
            String actions="{\"propose\":\"自定义 {worldName}\"}",modules="{\"geography\":\"保持\"}";
            execute(db,"insert into world_prompt_templates(id,user_id,modules_json,action_templates_json) values(?,?,?,?)",UUID.randomUUID(),owner,modules,actions);
            assertEquals(1,Flyway.configure().dataSource(url,user,password).target("48").load().migrate().migrationsExecuted);
            assertNull(scalar(db,"select timeline_json from worlds where id=?",world));assertEquals(hash,scalar(db,"select draft_content_hash from worlds where id=?",world));
            assertEquals(snapshot,scalar(db,"select snapshot_json from world_versions where id=?",version));assertEquals(hash,scalar(db,"select content_hash from world_versions where id=?",version));
            assertEquals(snapshot,store.write(store.parse(snapshot)));assertEquals(hash,store.hash(store.parse(snapshot)));
            assertEquals(4L,((Number)scalar(db,"select world_binding_revision from stories where id=?",story)).longValue());
            assertEquals(actions,scalar(db,"select action_templates_json from world_prompt_templates where user_id=?",owner));assertEquals(modules,scalar(db,"select modules_json from world_prompt_templates where user_id=?",owner));
            for(String table:List.of("story_world_eras","world_copy_receipts"))assertEquals(0L,((Number)scalar(db,"select count(*) from "+table)).longValue());
            assertEquals(0,Flyway.configure().dataSource(url,user,password).target("48").load().migrate().migrationsExecuted);
        }
    }
    private static void bind(PreparedStatement s,Object[] values)throws SQLException{for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof UUID u)v=ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();s.setObject(i+1,v);}}
    private static void execute(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);s.executeUpdate();}}
    private static Object scalar(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);try(var r=s.executeQuery()){assertTrue(r.next());return r.getObject(1);}}}
}
