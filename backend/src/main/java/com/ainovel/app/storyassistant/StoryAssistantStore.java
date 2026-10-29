package com.ainovel.app.storyassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.storyassistant.StoryAssistantErrors.*;

@Service
public class StoryAssistantStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public StoryAssistantStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public record Session(UUID id, UUID storyId, UUID userId, String title, Instant createdAt, Instant updatedAt) {}
    public record Turn(UUID id, UUID sessionId, String requestHash, UUID operationId, String frozenJson,
            String planRequestJson, String planResultJson, String finalRequestJson, String finalResultJson,
            String readsJson, String resultJson, Instant createdAt) {}
    public record Proposal(UUID id, UUID turnId, String status, long revision, String dataJson, Instant createdAt) {}
    public record Receipt(String requestHash, String responseJson) {}
    public List<Session> sessions(UUID story, UUID user) {
        return jdbc.query("select * from story_assistant_sessions where story_id=? and user_id=? order by updated_at desc,id desc limit 50",
                (r,n)->session(r),bytes(story),bytes(user));
    }
    public Session session(UUID id, boolean lock) {
        return jdbc.query("select * from story_assistant_sessions where id=?"+(lock?" for update":""),
                (r,n)->session(r),bytes(id)).stream().findFirst().orElseThrow(StoryAssistantErrors::missing);
    }
    public Session createSession(UUID story, UUID user, String title) {
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("insert into story_assistant_sessions(id,story_id,user_id,title,created_at,updated_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(story),bytes(user),title,Timestamp.from(now),Timestamp.from(now));
        return session(id,false);
    }
    public void rename(UUID id, String title) { jdbc.update("update story_assistant_sessions set title=?,updated_at=CURRENT_TIMESTAMP(6) where id=?",title,bytes(id)); }
    public void touch(UUID id) { jdbc.update("update story_assistant_sessions set updated_at=CURRENT_TIMESTAMP(6) where id=?",bytes(id)); }
    public Turn turn(UUID id) { return jdbc.query("select * from story_assistant_turns where id=?",(r,n)->turn(r),bytes(id)).stream().findFirst().orElseThrow(StoryAssistantErrors::missing); }
    public Turn replay(UUID session,String key) { return jdbc.query("select * from story_assistant_turns where session_id=? and request_key=?",(r,n)->turn(r),bytes(session),key).stream().findFirst().orElse(null); }
    public List<Turn> turns(UUID session,Instant before,UUID beforeId,int limit) {
        if(before==null)return jdbc.query("select * from story_assistant_turns where session_id=? order by created_at desc,id desc limit ?",(r,n)->turn(r),bytes(session),limit);
        return jdbc.query("select * from story_assistant_turns where session_id=? and (created_at<? or (created_at=? and id<?)) order by created_at desc,id desc limit ?",
                (r,n)->turn(r),bytes(session),Timestamp.from(before),Timestamp.from(before),bytes(beforeId),limit);
    }
    public UUID createTurn(UUID session,String key,String hash,Object frozen) {
        UUID id=UUID.randomUUID();jdbc.update("insert into story_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(session),key,hash,write(frozen),Timestamp.from(Instant.now()));return id;
    }
    public void attach(UUID turn,UUID operation) { jdbc.update("update story_assistant_turns set operation_id=? where id=?",bytes(operation),bytes(turn)); }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void checkpoint(UUID turn,String column,Object data) {
        if(!Set.of("plan_request_json","plan_result_json","final_request_json","final_result_json","reads_json").contains(column))throw new IllegalArgumentException();
        jdbc.update("update story_assistant_turns set "+column+"=? where id=? and "+column+" is null",write(data),bytes(turn));
    }
    public void finish(UUID turn,Object data) { jdbc.update("update story_assistant_turns set result_json=? where id=? and result_json is null",write(data),bytes(turn)); }
    public Proposal proposal(UUID id,boolean lock) { return jdbc.query("select * from story_assistant_proposals where id=?"+(lock?" for update":""),(r,n)->proposal(r),bytes(id)).stream().findFirst().orElseThrow(StoryAssistantErrors::missing); }
    public UUID createProposal(UUID turn,int index,Object data) {
        var found=jdbc.query("select id from story_assistant_proposals where turn_id=? and item_index=?",(r,n)->uuid(r.getBytes(1)),bytes(turn),index);
        if(!found.isEmpty())return found.getFirst(); UUID id=UUID.randomUUID();
        jdbc.update("insert into story_assistant_proposals(id,turn_id,item_index,status,revision,data_json,created_at) values(?,?,?,'READY',0,?,?)",bytes(id),bytes(turn),index,write(data),Timestamp.from(Instant.now()));return id;
    }
    public void saveProposal(UUID id,String status,Object data) { jdbc.update("update story_assistant_proposals set status=?,revision=revision+1,data_json=? where id=?",status,write(data),bytes(id)); }
    public Receipt receipt(UUID story,String scope,String key) { return jdbc.query("select request_hash,response_json from story_assistant_receipts where story_id=? and scope_key=? and request_key=?",(r,n)->new Receipt(r.getString(1),r.getString(2)),bytes(story),scope,key).stream().findFirst().orElse(null); }
    public void receipt(UUID story,String scope,String key,String hash,Object result) { jdbc.update("insert into story_assistant_receipts(story_id,scope_key,request_key,request_hash,response_json) values(?,?,?,?,?)",bytes(story),scope,key,hash,write(result)); }
    public String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    public <T>T read(String value,Class<T> type) { try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("STORY_ASSISTANT_STORED_DATA_INVALID",e);} }
    private static UUID nullable(byte[] value){return value==null?null:uuid(value);}
    private static Session session(ResultSet r)throws SQLException { return new Session(uuid(r.getBytes("id")),uuid(r.getBytes("story_id")),uuid(r.getBytes("user_id")),r.getString("title"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()); }
    private static Turn turn(ResultSet r)throws SQLException { return new Turn(uuid(r.getBytes("id")),uuid(r.getBytes("session_id")),r.getString("request_hash"),nullable(r.getBytes("operation_id")),r.getString("frozen_json"),r.getString("plan_request_json"),r.getString("plan_result_json"),r.getString("final_request_json"),r.getString("final_result_json"),r.getString("reads_json"),r.getString("result_json"),r.getTimestamp("created_at").toInstant()); }
    private static Proposal proposal(ResultSet r)throws SQLException {return new Proposal(uuid(r.getBytes("id")),uuid(r.getBytes("turn_id")),r.getString("status"),r.getLong("revision"),r.getString("data_json"),r.getTimestamp("created_at").toInstant());}
}
