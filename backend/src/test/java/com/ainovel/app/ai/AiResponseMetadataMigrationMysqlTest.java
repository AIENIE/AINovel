package com.ainovel.app.ai;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="AIENIE_AUDIT_MYSQL_URL",matches="jdbc:mysql:.*")
class AiResponseMetadataMigrationMysqlTest {
    @Test void emptyDatabaseAndV48UpgradeKeepHistoricalResultsAndStoreMetadataWithoutBackfill() throws Exception {
        String url=System.getenv("AIENIE_AUDIT_MYSQL_URL"),user=System.getenv("AIENIE_AUDIT_MYSQL_USERNAME"),password=System.getenv("AIENIE_AUDIT_MYSQL_PASSWORD");
        if(url==null||!url.matches("jdbc:mysql://[^/]+/aienie_novel_audit_test_[A-Za-z0-9_]+(?:\\?.*)?"))throw new IllegalArgumentException("Isolated schema required");
        var latest=Flyway.configure().dataSource(url,user,password).target("49").cleanDisabled(false).load();
        latest.clean();assertEquals(49,latest.migrate().migrationsExecuted);
        try(var db=DriverManager.getConnection(url,user,password)) {
            assertEquals("longtext",scalar(db,"select data_type from information_schema.columns where table_schema=database() and table_name='ai_credit_reservations' and column_name='result_metadata_json'"));
            assertEquals("YES",scalar(db,"select is_nullable from information_schema.columns where table_schema=database() and table_name='ai_credit_reservations' and column_name='result_metadata_json'"));
        }
        latest.clean();Flyway.configure().dataSource(url,user,password).target("48").load().migrate();
        try(var db=DriverManager.getConnection(url,user,password)) {
            UUID owner=UUID.randomUUID(),reservation=UUID.randomUUID();
            execute(db,"insert into users(id,username,email,password_hash,banned,credits) values(?,?,?,'x',false,0)",owner,"metadata-upgrade","metadata-upgrade@example.invalid");
            execute(db,"insert into ai_credit_reservations(id,user_id,idempotency_key,request_hash,reference_type,reference_id,reserved_amount,settled_amount,status,result_content,prompt_tokens,completion_tokens,cache_tokens,attempt_count,actual_model_key) values(?,?,'original','frozen-hash','AI_USAGE','original',10,1,'COMPLETED','旧正文\\n完整保留',100,8192,20,1,'test-model')",reservation,owner);
            String before=(String)scalar(db,"select concat_ws('|',idempotency_key,request_hash,status,result_content,prompt_tokens,completion_tokens,cache_tokens,reserved_amount,settled_amount,actual_model_key) from ai_credit_reservations where id=?",reservation);
            assertEquals(1,latest.migrate().migrationsExecuted);
            assertNull(scalar(db,"select result_metadata_json from ai_credit_reservations where id=?",reservation));
            assertEquals(before,scalar(db,"select concat_ws('|',idempotency_key,request_hash,status,result_content,prompt_tokens,completion_tokens,cache_tokens,reserved_amount,settled_amount,actual_model_key) from ai_credit_reservations where id=?",reservation));
            String metadata="{\"version\":1,\"finishReason\":\"LENGTH\",\"reasoningTokens\":8192,\"usageAuthoritative\":true}";
            execute(db,"update ai_credit_reservations set result_metadata_json=? where id=?",metadata,reservation);
            assertEquals(metadata,scalar(db,"select result_metadata_json from ai_credit_reservations where id=?",reservation));
            assertEquals(0,latest.migrate().migrationsExecuted);
        }
    }
    private static void bind(PreparedStatement s,Object[] values)throws SQLException {for(int i=0;i<values.length;i++){Object v=values[i];if(v instanceof UUID u)v=ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();s.setObject(i+1,v);}}
    private static void execute(Connection c,String sql,Object...values)throws SQLException {try(var s=c.prepareStatement(sql)){bind(s,values);s.executeUpdate();}}
    private static Object scalar(Connection c,String sql,Object...values)throws SQLException {try(var s=c.prepareStatement(sql)){bind(s,values);try(var r=s.executeQuery()){assertTrue(r.next());return r.getObject(1);}}}
}
