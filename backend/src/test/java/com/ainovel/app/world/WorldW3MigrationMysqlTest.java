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
class WorldW3MigrationMysqlTest {
    @Test void v45UpgradePreservesTemplatesSnapshotsHashesAndBindings() throws Exception {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL"),user=System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),password=System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        Flyway.configure().dataSource(url,user,password).target("45").load().migrate();
        try(var db=DriverManager.getConnection(url,user,password)) {
            UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),version=UUID.randomUUID(),story=UUID.randomUUID();
            var store=new WorldEntryStore(null,null,new ObjectMapper());
            String snapshot=store.write(new WorldDraftDtos.Content("旧世界","简介",List.of(),"意图","备注",List.of(),List.of(),Map.of(),"{}"));
            String hash=store.hash(store.parse(snapshot));
            execute(db,"insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,0)",owner,"w3-upgrade","w3-upgrade@example.invalid");
            execute(db,"insert into worlds(id,user_id,name,status,version,revision,modules_json,themes_json,module_progress_json,draft_content_hash,legacy_modules_json,tagline,creative_intent,notes) values(?,?,'旧世界','active','v1',7,'{}','[]','{}',?,'{}','简介','意图','备注')",world,owner,hash);
            execute(db,"insert into world_versions(id,world_id,sequence_number,origin,legacy_version,draft_revision,content_hash,snapshot_json,release_note,request_key,created_at) values(?,?,1,'PUBLISHED','v1',7,?,?,'原发布说明','old-publish',UTC_TIMESTAMP(6))",version,world,hash,snapshot);
            execute(db,"insert into stories(id,user_id,title,world_id,world_version_id,world_binding_revision) values(?,?,'固定小说',?,?,4)",story,owner,world.toString(),version);
            String modules="{\"geography\":\"自定义 {worldName}\"}",finals="{\"geography\":\"保留潮汐约束\"}",refine="自定义字段精修 {text}";
            execute(db,"insert into world_prompt_templates(id,user_id,modules_json,final_templates_json,field_refine) values(?,?,?,?,?)",UUID.randomUUID(),owner,modules,finals,refine);
            assertEquals(1,Flyway.configure().dataSource(url,user,password).target("46").load().migrate().migrationsExecuted);
            assertNull(scalar(db,"select overview from worlds where id=?",world));assertEquals(hash,scalar(db,"select draft_content_hash from worlds where id=?",world));
            assertEquals(snapshot,scalar(db,"select snapshot_json from world_versions where id=?",version));assertEquals(hash,scalar(db,"select content_hash from world_versions where id=?",version));assertEquals("原发布说明",scalar(db,"select release_note from world_versions where id=?",version));
            assertEquals(hash,store.hash(store.parse(snapshot)));assertEquals(4L,((Number)scalar(db,"select world_binding_revision from stories where id=?",story)).longValue());
            assertEquals(modules,scalar(db,"select modules_json from world_prompt_templates where user_id=?",owner));assertEquals(finals,scalar(db,"select final_templates_json from world_prompt_templates where user_id=?",owner));assertEquals(refine,scalar(db,"select field_refine from world_prompt_templates where user_id=?",owner));assertNull(scalar(db,"select action_templates_json from world_prompt_templates where user_id=?",owner));
            for(String table:List.of("world_assistant_sessions","world_assistant_turns","world_assistant_proposals","world_assistant_receipts"))assertEquals(0L,((Number)scalar(db,"select count(*) from "+table)).longValue());
            assertEquals(0,Flyway.configure().dataSource(url,user,password).target("46").load().migrate().migrationsExecuted);
        }
    }
    private static void bind(PreparedStatement s,Object[] values)throws SQLException{
        for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof UUID u)v=ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();s.setObject(i+1,v);}
    }
    private static void execute(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);s.executeUpdate();}}
    private static Object scalar(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);try(var r=s.executeQuery()){assertTrue(r.next());return r.getObject(1);}}}
}
