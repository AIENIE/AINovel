package com.ainovel.app.assistant;

import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;

/** Requests and returned text commit separately from worker completion and author decisions. */
@Service
public class AssistantStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AssistantStore(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public record Session(UUID id,UUID userId,UUID manuscriptId,UUID branchId,UUID sceneId) {}
    public record Turn(UUID id,UUID sessionId,String requestKey,String requestHash,UUID operationId,String frozenJson,
            String planRequestJson,String planResultJson,String finalRequestJson,String finalResultJson,
            String readsJson,String resultJson,Instant createdAt) {}
    public record Proposal(UUID id,UUID turnId,String status,String dataJson,String receiptsJson) {}
    public Session session(UUID user,UUID manuscript,UUID branch,UUID scene){
        return jdbc.query("select * from writing_assistant_sessions where user_id=? and manuscript_id=? and branch_id=? and scene_id=?",
                (r,n)->session(r),bytes(user),bytes(manuscript),bytes(branch),bytes(scene)).stream().findFirst().orElse(null);
    }
    public Session session(UUID id){return jdbc.query("select * from writing_assistant_sessions where id=?",(r,n)->session(r),bytes(id)).stream().findFirst().orElseThrow(AssistantStore::missing);}
    public Session createSession(UUID user,UUID manuscript,UUID branch,UUID scene){
        UUID id=UUID.randomUUID();
        jdbc.update("insert into writing_assistant_sessions(id,user_id,manuscript_id,branch_id,scene_id,created_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(user),bytes(manuscript),bytes(branch),bytes(scene),Timestamp.from(Instant.now()));
        return new Session(id,user,manuscript,branch,scene);
    }
    public void lockSession(UUID id){jdbc.queryForObject("select id from writing_assistant_sessions where id=? for update",byte[].class,bytes(id));}
    public Turn turn(UUID id){return jdbc.query("select * from writing_assistant_turns where id=?",(r,n)->turn(r),bytes(id)).stream().findFirst().orElseThrow(AssistantStore::missing);}
    public Turn replay(UUID session,String key){return jdbc.query("select * from writing_assistant_turns where session_id=? and request_key=?",(r,n)->turn(r),bytes(session),key).stream().findFirst().orElse(null);}
    public List<Turn> turns(UUID session,Instant before,UUID beforeId,int limit){
        if(before==null)return jdbc.query("select * from writing_assistant_turns where session_id=? order by created_at desc,id desc limit ?",(r,n)->turn(r),bytes(session),limit);
        return jdbc.query("select * from writing_assistant_turns where session_id=? and (created_at<? or (created_at=? and id<?)) order by created_at desc,id desc limit ?",
                (r,n)->turn(r),bytes(session),Timestamp.from(before),Timestamp.from(before),bytes(beforeId),limit);
    }
    public UUID createTurn(UUID session,String key,String hash,Object frozen){
        UUID id=UUID.randomUUID();jdbc.update("insert into writing_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(session),key,hash,write(frozen),Timestamp.from(Instant.now()));return id;
    }
    public void attach(UUID turn,UUID operation){jdbc.update("update writing_assistant_turns set operation_id=? where id=?",bytes(operation),bytes(turn));}
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void checkpoint(UUID turn,String column,Object value){
        if(!Set.of("plan_request_json","plan_result_json","final_request_json","final_result_json","reads_json").contains(column))throw new IllegalArgumentException();
        jdbc.update("update writing_assistant_turns set "+column+"=? where id=? and "+column+" is null",write(value),bytes(turn));
    }
    public void finish(UUID turn,Object value){jdbc.update("update writing_assistant_turns set result_json=? where id=? and result_json is null",write(value),bytes(turn));}
    public Proposal proposal(UUID id,boolean lock){return jdbc.query("select * from writing_assistant_proposals where id=?"+(lock?" for update":""),(r,n)->proposal(r),bytes(id)).stream().findFirst().orElseThrow(AssistantStore::missing);}
    public Proposal proposalForTurn(UUID turn){return jdbc.query("select * from writing_assistant_proposals where turn_id=?",(r,n)->proposal(r),bytes(turn)).stream().findFirst().orElse(null);}
    public UUID createProposal(UUID turn,Object data){
        var old=proposalForTurn(turn);if(old!=null)return old.id();UUID id=UUID.randomUUID();
        jdbc.update("insert into writing_assistant_proposals(id,turn_id,status,data_json,receipts_json,created_at) values(?,?,'READY',?,'{}',?)",bytes(id),bytes(turn),write(data),Timestamp.from(Instant.now()));return id;
    }
    public void saveProposal(UUID id,String status,Object data,Object receipts){jdbc.update("update writing_assistant_proposals set status=?,data_json=?,receipts_json=? where id=?",status,write(data),write(receipts),bytes(id));}
    public String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);}}
    public <T>T read(String value,Class<T> type){try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("ASSISTANT_STORED_DATA_INVALID",e);}}
    private static Session session(ResultSet r)throws SQLException{return new Session(uuid(r.getBytes("id")),uuid(r.getBytes("user_id")),uuid(r.getBytes("manuscript_id")),uuid(r.getBytes("branch_id")),uuid(r.getBytes("scene_id")));}
    private static UUID nullable(byte[] value){return value==null?null:uuid(value);}
    private static Turn turn(ResultSet r)throws SQLException{return new Turn(uuid(r.getBytes("id")),uuid(r.getBytes("session_id")),r.getString("request_key"),r.getString("request_hash"),nullable(r.getBytes("operation_id")),r.getString("frozen_json"),r.getString("plan_request_json"),r.getString("plan_result_json"),r.getString("final_request_json"),r.getString("final_result_json"),r.getString("reads_json"),r.getString("result_json"),r.getTimestamp("created_at").toInstant());}
    private static Proposal proposal(ResultSet r)throws SQLException{return new Proposal(uuid(r.getBytes("id")),uuid(r.getBytes("turn_id")),r.getString("status"),r.getString("data_json"),r.getString("receipts_json"));}
    private static ApiStatusException missing(){return new ApiStatusException(HttpStatus.NOT_FOUND,"ASSISTANT_NOT_FOUND");}
}
