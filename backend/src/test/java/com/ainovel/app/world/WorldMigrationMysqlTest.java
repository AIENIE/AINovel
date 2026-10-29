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
class WorldMigrationMysqlTest {
    @Test void historicalAndUnknownContentMigratesWithoutInventingPublishedHistory() throws Exception {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL"),user=System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),password=System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        Flyway.configure().dataSource(url,user,password).target("41").load().migrate();
        try(var db=DriverManager.getConnection(url,user,password)){
            UUID owner=UUID.randomUUID(),world=UUID.randomUUID(),unbound=UUID.randomUUID(),story=UUID.randomUUID(),outline=UUID.randomUUID(),manuscript=UUID.randomUUID();
            String original="{\"geography\":{\"terrain\":\"岛屿\",\"unknown\":\"原始未知字段\",\"object\":{\"nested\":true}},\"legacy\":\"旧文本模块\"}";
            execute(db,"insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,0)",owner,"migration","migration@example.invalid");
            execute(db,"insert into worlds(id,user_id,name,status,version,revision,modules_json,themes_json,module_progress_json) values(?,?,'群岛','draft','0.1.7',0,?,'[]','{}')",world,owner,original);
            execute(db,"insert into worlds(id,user_id,name,status,version,revision,modules_json,themes_json,module_progress_json) values(?,?,'未绑定','draft','0.1.0',0,?,'[]','{}')",unbound,owner,"not-json");
            execute(db,"insert into stories(id,user_id,title,world_id) values(?,?,'故事',?)",story,owner,world.toString());
            execute(db,"insert into outlines(id,story_id,title,world_id,content_json,revision) values(?,?,'旧覆盖',?,'{}',0)",outline,story,world.toString());
            execute(db,"insert into manuscripts(id,outline_id,title,world_id) values(?,?,'旧稿件覆盖',?)",manuscript,outline,world.toString());
            Flyway.configure().dataSource(url,user,password).initSql("SET time_zone = '+08:00'").target("44").load().migrate();
            String baselineBeforeUtcRepair=(String)scalar(db,"select snapshot_json from world_versions where world_id=?",world);
            Flyway.configure().dataSource(url,user,password).initSql("SET time_zone = '-05:00'").load().migrate();
            assertEquals(baselineBeforeUtcRepair,scalar(db,"select snapshot_json from world_versions where world_id=?",world));
            assertEquals(((Number)scalar(db,"select unix_timestamp(installed_on) from flyway_schema_history where version='43'")).longValue(),
                ((Number)scalar(db,"select timestampdiff(SECOND,'1970-01-01 00:00:00',created_at) from world_versions where world_id=?",world)).longValue());
            assertEquals(original,scalar(db,"select legacy_modules_json from worlds where id=?",world));
            assertEquals(4L,((Number)scalar(db,"select count(*) from world_entries where world_id=?",world)).longValue());
            assertEquals("原始未知字段",scalar(db,"select content from world_entries where world_id=? and legacy_field_key='unknown'",world));
            assertEquals("not-json",scalar(db,"select content from world_entries where world_id=?",unbound));
            assertEquals(0L,((Number)scalar(db,"select count(*) from world_versions where origin='PUBLISHED'")).longValue());
            assertEquals(0L,((Number)scalar(db,"select count(*) from world_versions where world_id=?",unbound)).longValue());
            String snapshot=(String)scalar(db,"select snapshot_json from world_versions where world_id=?",world);
            assertEquals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(snapshot.getBytes(java.nio.charset.StandardCharsets.UTF_8))),
                scalar(db,"select content_hash from world_versions where world_id=?",world));
            var content=new ObjectMapper().readValue(snapshot,WorldDraftDtos.Content.class);
            assertEquals(original,content.legacyModulesJson());assertEquals(4,content.entries().size());
            assertEquals("MIGRATION_BASELINE",scalar(db,"select origin from world_versions where world_id=?",world));
            for(String table:List.of("stories","outlines","manuscripts"))assertEquals(1L,((Number)scalar(db,"select count(*) from "+table+" where world_version_id is not null")).longValue());
            assertEquals(0,Flyway.configure().dataSource(url,user,password).load().migrate().migrationsExecuted);
        }
    }
    private static void bind(PreparedStatement s,Object[] values)throws SQLException{
        for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof UUID u)v=ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();s.setObject(i+1,v);}
    }
    private static void execute(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);s.executeUpdate();}}
    private static Object scalar(Connection c,String sql,Object...values)throws SQLException{try(var s=c.prepareStatement(sql)){bind(s,values);try(var r=s.executeQuery()){assertTrue(r.next());return r.getObject(1);}}}
}
