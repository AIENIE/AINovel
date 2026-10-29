package com.ainovel.app.worldassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;
import static com.ainovel.app.worldassistant.WorldAssistantErrors.*;

@Service
public class WorldAssistantStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public WorldAssistantStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
    public record Session(UUID id, UUID worldId, UUID userId, String title, Instant createdAt, Instant updatedAt) {}
    public record Turn(UUID id, UUID sessionId, String requestHash, UUID operationId, String frozenJson,
            String planRequestJson, String planResultJson, String finalRequestJson, String finalResultJson,
            String readsJson, String resultJson, Instant createdAt) {}
    public record Proposal(UUID id, UUID turnId, String status, long revision, String dataJson, Instant createdAt) {}
    public record Receipt(String requestHash, String responseJson) {}
    public List<Session> sessions(UUID world, UUID user) {
        return jdbc.query("select * from world_assistant_sessions where world_id=? and user_id=? order by updated_at desc,id desc limit 50",
                (r,n)->session(r),bytes(world),bytes(user));
    }
    public Session session(UUID id, boolean lock) {
        return jdbc.query("select * from world_assistant_sessions where id=?"+(lock?" for update":""),
                (r,n)->session(r),bytes(id)).stream().findFirst().orElseThrow(WorldAssistantErrors::missing);
    }
    public Session createSession(UUID world, UUID user, String title) {
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        jdbc.update("insert into world_assistant_sessions(id,world_id,user_id,title,created_at,updated_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(world),bytes(user),title,Timestamp.from(now),Timestamp.from(now));
        return session(id,false);
    }
    public void rename(UUID id, String title) { jdbc.update("update world_assistant_sessions set title=?,updated_at=CURRENT_TIMESTAMP(6) where id=?",title,bytes(id)); }
    public void touch(UUID id) { jdbc.update("update world_assistant_sessions set updated_at=CURRENT_TIMESTAMP(6) where id=?",bytes(id)); }
    public Turn turn(UUID id) { return jdbc.query("select * from world_assistant_turns where id=?",(r,n)->turn(r),bytes(id)).stream().findFirst().orElseThrow(WorldAssistantErrors::missing); }
    public Turn replay(UUID session,String key) { return jdbc.query("select * from world_assistant_turns where session_id=? and request_key=?",(r,n)->turn(r),bytes(session),key).stream().findFirst().orElse(null); }
    public List<Turn> turns(UUID session,Instant before,UUID beforeId,int limit) {
        if(before==null)return jdbc.query("select * from world_assistant_turns where session_id=? order by created_at desc,id desc limit ?",(r,n)->turn(r),bytes(session),limit);
        return jdbc.query("select * from world_assistant_turns where session_id=? and (created_at<? or (created_at=? and id<?)) order by created_at desc,id desc limit ?",
                (r,n)->turn(r),bytes(session),Timestamp.from(before),Timestamp.from(before),bytes(beforeId),limit);
    }
    public UUID createTurn(UUID session,String key,String hash,Object frozen) {
        UUID id=UUID.randomUUID();jdbc.update("insert into world_assistant_turns(id,session_id,request_key,request_hash,frozen_json,created_at) values(?,?,?,?,?,?)",
                bytes(id),bytes(session),key,hash,write(frozen),Timestamp.from(Instant.now()));return id;
    }
    public void attach(UUID turn,UUID operation) { jdbc.update("update world_assistant_turns set operation_id=? where id=?",bytes(operation),bytes(turn)); }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void checkpoint(UUID turn,String column,Object data) {
        if(!Set.of("plan_request_json","plan_result_json","final_request_json","final_result_json","reads_json").contains(column))throw new IllegalArgumentException();
        jdbc.update("update world_assistant_turns set "+column+"=? where id=? and "+column+" is null",write(data),bytes(turn));
    }
    public void finish(UUID turn,Object data) { jdbc.update("update world_assistant_turns set result_json=? where id=? and result_json is null",write(data),bytes(turn)); }
    public Proposal proposal(UUID id,boolean lock) { return jdbc.query("select * from world_assistant_proposals where id=?"+(lock?" for update":""),(r,n)->proposal(r),bytes(id)).stream().findFirst().orElseThrow(WorldAssistantErrors::missing); }
    public UUID createProposal(UUID turn,int index,Object data) {
        var found=jdbc.query("select id from world_assistant_proposals where turn_id=? and item_index=?",(r,n)->uuid(r.getBytes(1)),bytes(turn),index);
        if(!found.isEmpty())return found.getFirst(); UUID id=UUID.randomUUID();
        jdbc.update("insert into world_assistant_proposals(id,turn_id,item_index,status,resource_type,revision,data_json,created_at) values(?,?,?,'READY',?,0,?,?)",bytes(id),bytes(turn),index,json.valueToTree(data).path("resourceType").asText(),write(data),Timestamp.from(Instant.now()));return id;
    }
    public List<Proposal> proposals(UUID world,String status,String type,UUID session,Instant before,UUID beforeId,int limit) {
        StringBuilder sql=new StringBuilder("select p.* from world_assistant_proposals p join world_assistant_turns t on t.id=p.turn_id join world_assistant_sessions s on s.id=t.session_id where s.world_id=?");
        List<Object> args=new ArrayList<>();args.add(bytes(world));
        if(status!=null&&!status.isBlank()){sql.append(" and p.status=?");args.add(status);}
        if(type!=null&&!type.isBlank()){sql.append(" and p.resource_type=?");args.add(type);}
        if(session!=null){sql.append(" and s.id=?");args.add(bytes(session));}
        if(before!=null){sql.append(" and (p.created_at<? or (p.created_at=? and p.id<?))");args.add(Timestamp.from(before));args.add(Timestamp.from(before));args.add(bytes(beforeId));}
        sql.append(" order by p.created_at desc,p.id desc limit ?");args.add(limit);
        return jdbc.query(sql.toString(),(r,n)->proposal(r),args.toArray());
    }
    public void saveProposal(UUID id,String status,Object data) { jdbc.update("update world_assistant_proposals set status=?,revision=revision+1,data_json=? where id=?",status,write(data),bytes(id)); }
    public Receipt receipt(UUID world,String scope,String key) { return jdbc.query("select request_hash,response_json from world_assistant_receipts where world_id=? and scope_key=? and request_key=?",(r,n)->new Receipt(r.getString(1),r.getString(2)),bytes(world),scope,key).stream().findFirst().orElse(null); }
    public void receipt(UUID world,String scope,String key,String hash,Object result) { jdbc.update("insert into world_assistant_receipts(world_id,scope_key,request_key,request_hash,response_json) values(?,?,?,?,?)",bytes(world),scope,key,hash,write(result)); }
    public String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalStateException(e);} }
    public <T>T read(String value,Class<T> type) { try{return json.readValue(value,type);}catch(Exception e){throw new IllegalStateException("WORLD_ASSISTANT_STORED_DATA_INVALID",e);} }
    private static UUID nullable(byte[] value){return value==null?null:uuid(value);}
    private static Session session(ResultSet r)throws SQLException { return new Session(uuid(r.getBytes("id")),uuid(r.getBytes("world_id")),uuid(r.getBytes("user_id")),r.getString("title"),r.getTimestamp("created_at").toInstant(),r.getTimestamp("updated_at").toInstant()); }
    private static Turn turn(ResultSet r)throws SQLException { return new Turn(uuid(r.getBytes("id")),uuid(r.getBytes("session_id")),r.getString("request_hash"),nullable(r.getBytes("operation_id")),r.getString("frozen_json"),r.getString("plan_request_json"),r.getString("plan_result_json"),r.getString("final_request_json"),r.getString("final_result_json"),r.getString("reads_json"),r.getString("result_json"),r.getTimestamp("created_at").toInstant()); }
    private static Proposal proposal(ResultSet r)throws SQLException {return new Proposal(uuid(r.getBytes("id")),uuid(r.getBytes("turn_id")),r.getString("status"),r.getLong("revision"),r.getString("data_json"),r.getTimestamp("created_at").toInstant());}
}
