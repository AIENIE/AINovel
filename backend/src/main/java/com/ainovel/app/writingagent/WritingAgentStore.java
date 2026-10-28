package com.ainovel.app.writingagent;

import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import static com.ainovel.app.modelconfig.AiModelConfigurationService.bytes;
import static com.ainovel.app.writingagent.WritingAgentDtos.error;

/** Journal, not chat history, is the replay authority. Private requests never leave this store. */
@Repository
public class WritingAgentStore {
    final JdbcTemplate jdbc;
    final ObjectMapper json;
    private final TransactionTemplate isolated;
    public WritingAgentStore(JdbcTemplate jdbc,ObjectMapper json,PlatformTransactionManager manager){
        this.jdbc=jdbc;this.json=json;isolated=new TransactionTemplate(manager);isolated.setPropagationBehavior(3);
    }
    public record Run(UUID id,UUID userId,UUID manuscriptId,UUID branchId,UUID sceneId,String requestKey,
            String requestHash,String status,long revision,UUID operationId,String frozen,String state,
            BigDecimal maxCny,long maxCredits,String decision,Instant createdAt) {}
    public record Step(UUID id,UUID runId,int sequence,String key,String kind,String feature,String requestHash,
            String request,String result,String gatewayRequest,String budget,String gatewayResult,String settlement,
            BigDecimal reservedCny,BigDecimal settledCny,long reservedCredits,Long settledCredits) {}
    public Run run(UUID id){boolean writing=org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()&&!org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();return jdbc.query("select * from writing_agent_runs where id=?"+(writing?" for update":""),(r,n)->run(r),bytes(id)).stream().findFirst().orElseThrow(()->error("WRITING_AGENT_NOT_FOUND"));}
    public Run replay(UUID user,UUID manuscript,UUID scene,String key){return jdbc.query("select * from writing_agent_runs where user_id=? and manuscript_id=? and scene_id=? and request_key=?",(r,n)->run(r),bytes(user),bytes(manuscript),bytes(scene),key).stream().findFirst().orElse(null);}
    public List<Run> recent(UUID user,UUID manuscript,UUID scene){return jdbc.query("select * from writing_agent_runs where user_id=? and manuscript_id=? and scene_id=? order by created_at desc,id desc limit 20",(r,n)->run(r),bytes(user),bytes(manuscript),bytes(scene));}
    public void lock(UUID id){jdbc.queryForObject("select revision from writing_agent_runs where id=? for update",Long.class,bytes(id));}
    public UUID create(UUID user,WritingAgentDtos.Frozen f,String key,String hash){
        UUID id=UUID.randomUUID();jdbc.update("insert into writing_agent_runs(id,user_id,manuscript_id,branch_id,scene_id,request_key,request_hash,status,frozen_json,state_json,max_cost_cny,max_project_credits) values(?,?,?,?,?,?,?,'QUEUED',?,'{}',?,?)",
            bytes(id),bytes(user),bytes(f.manuscriptId()),bytes(f.branchId()),bytes(f.sceneId()),key,hash,write(f),f.maxCostCny(),f.maxProjectCredits());return id;
    }
    public void attach(UUID id,UUID operation){jdbc.update("update writing_agent_runs set operation_id=?,status='QUEUED',revision=revision+1,updated_at=current_timestamp(6) where id=?",bytes(operation),bytes(id));}
    public void state(UUID id,String status,Object state){var next=json.valueToTree(state);var previous=tree(run(id).state());if(next.isObject()&&previous.has("authorAnswers")&&!next.has("authorAnswers"))((com.fasterxml.jackson.databind.node.ObjectNode)next).set("authorAnswers",previous.get("authorAnswers"));jdbc.update("update writing_agent_runs set status=?,state_json=?,revision=revision+1,updated_at=current_timestamp(6) where id=? and status<>'CANCELLED'",status,write(next),bytes(id));}
    public void decision(UUID id,Object decision){jdbc.update("update writing_agent_runs set decision_json=?,status='ACCEPTED',revision=revision+1,updated_at=current_timestamp(6) where id=?",write(decision),bytes(id));}
    public List<Step> steps(UUID id){return jdbc.query("select * from writing_agent_steps where run_id=? order by sequence_number",(r,n)->step(r),bytes(id));}
    public Step step(UUID run,String key){return jdbc.query("select * from writing_agent_steps where run_id=? and call_key=?",(r,n)->step(r),bytes(run),key).stream().findFirst().orElse(null);}
    public Step request(UUID run,int sequence,String key,String kind,String feature,Object request){return isolated(()->{
        lock(run);String payload=write(request),hash=hash(payload);Step old=step(run,key);
        if(old!=null){if(!old.requestHash().equals(hash))throw error("WRITING_AGENT_REQUEST_CHANGED");return old;}
        UUID id=UUID.randomUUID();jdbc.update("insert into writing_agent_steps(id,run_id,sequence_number,call_key,kind,feature_key,request_hash,request_json) values(?,?,?,?,?,?,?,?)",bytes(id),bytes(run),sequence,key,kind,feature,hash,payload);return step(run,key);
    });}
    public void result(UUID run,String key,Object result){isolated(()->{lock(run);jdbc.update("update writing_agent_steps set result_json=? where run_id=? and call_key=? and result_json is null",write(result),bytes(run),key);return null;});}
    public <T>T isolated(Supplier<T> work){return isolated.execute(s->work.get());}
    public String write(Object v){try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    public <T>T read(String v,Class<T> type){try{return json.readValue(v,type);}catch(Exception e){throw new IllegalStateException("WRITING_AGENT_SNAPSHOT_INVALID",e);}}
    public JsonNode tree(String v){try{return json.readTree(v==null?"{}":v);}catch(Exception e){throw new IllegalStateException("WRITING_AGENT_SNAPSHOT_INVALID",e);}}
    public static String hash(String value){return com.ainovel.app.modelconfig.AiActionPlanner.hash(value);}
    private Run run(ResultSet r)throws SQLException{return new Run(id(r,"id"),id(r,"user_id"),id(r,"manuscript_id"),id(r,"branch_id"),id(r,"scene_id"),r.getString("request_key"),r.getString("request_hash"),r.getString("status"),r.getLong("revision"),id(r,"operation_id"),r.getString("frozen_json"),r.getString("state_json"),r.getBigDecimal("max_cost_cny"),r.getLong("max_project_credits"),r.getString("decision_json"),r.getTimestamp("created_at").toInstant());}
    private Step step(ResultSet r)throws SQLException{return new Step(id(r,"id"),id(r,"run_id"),r.getInt("sequence_number"),r.getString("call_key"),r.getString("kind"),r.getString("feature_key"),r.getString("request_hash"),r.getString("request_json"),r.getString("result_json"),r.getString("gateway_request_base64"),r.getString("budget_json"),r.getString("gateway_result_base64"),r.getString("settlement_state"),r.getBigDecimal("reserved_cny"),r.getBigDecimal("settled_cny"),r.getLong("reserved_project_credits"),r.getObject("settled_project_credits")==null?null:r.getLong("settled_project_credits"));}
    private static UUID id(ResultSet r,String name)throws SQLException{byte[] b=r.getBytes(name);if(b==null)return null;var bb=java.nio.ByteBuffer.wrap(b);return new UUID(bb.getLong(),bb.getLong());}
}
