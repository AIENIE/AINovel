package com.ainovel.app.story;

import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;

@Service
public class OutlineAiStore {
    private final JdbcTemplate jdbc;private final ObjectMapper json;
    public OutlineAiStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Row(UUID id,UUID outlineId,UUID userId,UUID operationId,String requestHash,String action,String status,long revision,
            String frozenJson,String rawResultJson,String candidateJson,String receiptJson,Instant createdAt) {}
    private Row row(java.sql.ResultSet r)throws java.sql.SQLException {
        return new Row(uuid(r.getBytes("id")),uuid(r.getBytes("outline_id")),uuid(r.getBytes("user_id")),r.getBytes("operation_id")==null?null:uuid(r.getBytes("operation_id")),
                r.getString("request_hash"),r.getString("action"),r.getString("status"),r.getLong("revision"),r.getString("frozen_json"),r.getString("raw_result_json"),r.getString("candidate_json"),r.getString("receipt_json"),r.getTimestamp("created_at").toInstant());
    }
    public Row get(UUID id,boolean lock){return jdbc.query("select * from outline_ai_results where id=?"+(lock?" for update":""),(r,n)->row(r),bytes(id)).stream().findFirst().orElseThrow(()->new ApiStatusException(HttpStatus.NOT_FOUND,"OUTLINE_AI_RESULT_MISSING"));}
    public Row replay(UUID outline,String key){return jdbc.query("select * from outline_ai_results where outline_id=? and request_key=? for update",(r,n)->row(r),bytes(outline),key).stream().findFirst().orElse(null);}
    public List<Row> list(UUID outline){return jdbc.query("select * from outline_ai_results where outline_id=? order by created_at desc,id desc limit 50",(r,n)->row(r),bytes(outline));}
    public UUID create(UUID outline,UUID user,String key,String hash,String action,Object frozen){UUID id=UUID.randomUUID();jdbc.update("insert into outline_ai_results(id,outline_id,user_id,request_key,request_hash,action,status,frozen_json) values(?,?,?,?,?,?,'QUEUED',?)",bytes(id),bytes(outline),bytes(user),key,hash,action,write(frozen));return id;}
    public void attach(UUID id,UUID operation){jdbc.update("update outline_ai_results set operation_id=? where id=?",bytes(operation),bytes(id));}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void raw(UUID id,Object result){jdbc.update("update outline_ai_results set raw_result_json=? where id=? and raw_result_json is null",write(result),bytes(id));}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void candidate(UUID id,Object candidate,String status){jdbc.update("update outline_ai_results set candidate_json=?,status=?,revision=revision+1 where id=? and candidate_json is null",candidate==null?null:write(candidate),status,bytes(id));}
    public void applied(UUID id,Object receipt){jdbc.update("update outline_ai_results set status='APPLIED',revision=revision+1,receipt_json=? where id=?",write(receipt),bytes(id));}
    public String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public <T>T read(String text,Class<T> type){try{return json.readValue(text,type);}catch(Exception e){throw new IllegalStateException("OUTLINE_AI_STORED_DATA_INVALID",e);}}
    public JsonNode tree(String text){return read(text,JsonNode.class);}
}
