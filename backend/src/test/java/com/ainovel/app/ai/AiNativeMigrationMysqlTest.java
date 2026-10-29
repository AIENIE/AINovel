package com.ainovel.app.ai;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
class AiNativeMigrationMysqlTest {
    @Test void emptyAndV49UpgradePreserveOriginalResultAndImmutableSnapshotBytes() throws Exception {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL"),user=System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),password=System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        var latest=Flyway.configure().dataSource(url,user,password).target("50").cleanDisabled(false).load();
        latest.clean();assertEquals(50,latest.migrate().migrationsExecuted);
        try(var db=DriverManager.getConnection(url,user,password)) {
            assertEquals("YES",scalar(db,"select is_nullable from information_schema.columns where table_schema=database() and table_name='ai_credit_reservations' and column_name='result_native_response_json'"));
            assertEquals("longtext",scalar(db,"select data_type from information_schema.columns where table_schema=database() and table_name='ai_credit_reservations' and column_name='result_native_response_json'"));
            assertEquals(2L,((Number)scalar(db,"select count(*) from information_schema.tables where table_schema=database() and table_name in ('writing_agent_runs','writing_agent_steps')")).longValue());
        }
        latest.clean();Flyway.configure().dataSource(url,user,password).target("49").load().migrate();
        try(var db=DriverManager.getConnection(url,user,password)) {
            UUID owner=UUID.randomUUID(),reservation=UUID.randomUUID(),world=UUID.randomUUID(),version=UUID.randomUUID();
            execute(db,"insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,0)",owner,"native-upgrade","native-upgrade@example.invalid");
            execute(db,"insert into ai_credit_reservations(id,user_id,idempotency_key,request_hash,reference_type,reference_id,reserved_amount,settled_amount,status,result_content,result_metadata_json,prompt_tokens,completion_tokens,cache_tokens,attempt_count,actual_model_key) values(?,?,'original','frozen-hash','AI_USAGE','original',10,1,'COMPLETED','旧正文完整保留','{\"version\":1,\"finishReason\":\"STOP\"}',100,20,0,1,'model')",reservation,owner);
            execute(db,"insert into worlds(id,user_id,name,status) values(?,?,'旧世界','draft')",world,owner);
            String snapshot="{\"name\":\"旧世界\",\"future_unknown\":{\"x\":1}}";
            execute(db,"insert into world_versions(id,world_id,sequence_number,origin,draft_revision,content_hash,snapshot_json,created_at) values(?,?,1,'PUBLISHED',1,SHA2(?,256),?,UTC_TIMESTAMP(6))",version,world,snapshot,snapshot);
            String before=(String)scalar(db,"select SHA2(concat_ws('|',idempotency_key,request_hash,status,result_content,result_metadata_json,prompt_tokens,completion_tokens,reserved_amount,settled_amount),256) from ai_credit_reservations where id=?",reservation);
            assertEquals(1,latest.migrate().migrationsExecuted);
            assertNull(scalar(db,"select result_native_response_json from ai_credit_reservations where id=?",reservation));
            assertEquals(before,scalar(db,"select SHA2(concat_ws('|',idempotency_key,request_hash,status,result_content,result_metadata_json,prompt_tokens,completion_tokens,reserved_amount,settled_amount),256) from ai_credit_reservations where id=?",reservation));
            assertEquals(snapshot,scalar(db,"select snapshot_json from world_versions where id=?",version));
            assertEquals(scalar(db,"select SHA2(snapshot_json,256) from world_versions where id=?",version),scalar(db,"select content_hash from world_versions where id=?",version));
            assertEquals(0,latest.migrate().migrationsExecuted);
        }
    }
    private static void bind(PreparedStatement s,Object[] values)throws SQLException {for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof UUID u)v=ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();s.setObject(i+1,v);}}
    private static void execute(Connection c,String sql,Object...values)throws SQLException {try(var s=c.prepareStatement(sql)){bind(s,values);s.executeUpdate();}}
    private static Object scalar(Connection c,String sql,Object...values)throws SQLException {try(var s=c.prepareStatement(sql)){bind(s,values);try(var r=s.executeQuery()){assertTrue(r.next());return r.getObject(1);}}}
}
