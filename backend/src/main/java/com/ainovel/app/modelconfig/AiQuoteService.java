package com.ainovel.app.modelconfig;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AiQuoteService {
    public record Request(String target,JsonNode body,Map<String,String> selections) {}
    public record QuoteStep(AiActionPlanner.Step step,AiModelConfigurationService.FeatureView feature,
            AiModelSelection selected,long estimatedCredits) {}
    public record View(String id,Instant expiresAt,List<QuoteStep> steps,long estimatedCredits) {}
    public record Pending(String id,String target,JsonNode body,String requestKey,String quoteId,String label) {}
    private final AiModelConfigurationService config;
    private final AiActionPlanner planner;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    public AiQuoteService(AiModelConfigurationService config,AiActionPlanner planner,JdbcTemplate jdbc,ObjectMapper json,TransactionTemplate tx) {
        this.config=config;this.planner=planner;this.jdbc=jdbc;this.json=json;this.tx=tx;
    }
    public View create(User user,Request request) {
        if(request.target()==null||!request.target().startsWith("/v")||request.target().length()>2000)throw AiModelConfigurationService.error("AI_ACTION_INVALID");
        String target=canonicalTarget(request.target());
        config.ensureInitialized();
        JsonNode body=request.body()==null?json.createObjectNode():request.body();
        var plan=planner.plan(user,target,body);
        return tx.execute(s->{
            long revision=jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
            requireNoUnresolvedOperation(user,target,body,"");
            var selections=new LinkedHashMap<String,AiModelSelection>();List<QuoteStep> steps=new ArrayList<>();
            for(var step:plan.steps()) {
                var feature=config.feature(step.featureKey(),user);
                if(feature.options().isEmpty())throw AiModelConfigurationService.error("AI_FEATURE_UNCONFIGURED");
                String requested=request.selections()==null?null:request.selections().get(step.featureKey());
                if(!feature.billingEnabled()) requested=feature.defaultOptionId();
                else if(requested==null) {
                    if(feature.preferenceInvalid())throw AiModelConfigurationService.error("AI_PREFERENCE_UNAVAILABLE");
                    requested=feature.preferredOptionId()==null?feature.defaultOptionId():feature.preferredOptionId();
                }
                String id=requested;
                var selected=feature.options().stream().filter(o->o.optionId().equals(id)).findFirst().orElseThrow(()->AiModelConfigurationService.error("AI_OPTION_UNAVAILABLE"));
                if(feature.unavailableOptions().contains(id))throw AiModelConfigurationService.error("AI_OPTION_UNAVAILABLE");
                selections.put(step.featureKey(),selected);
                steps.add(new QuoteStep(step,feature,selected,Math.multiplyExact(selected.cost(step.estimatedInput(),step.estimatedOutput()),maximumCalls(step.calls()))));
            }
            String id=UUID.randomUUID().toString();Instant expires=Instant.now().plusSeconds(600);
            jdbc.update("insert into ai_model_quotes(id,user_id,input_hash,target_path,request_json,business_hash,configuration_revision,selections_json,expires_at) values(?,?,?,?,?,?,?,?,?)",
                    id,AiModelConfigurationService.bytes(user.getId()),inputHash(target,body),target,config.write(body),plan.businessHash(),revision,
                    config.write(new AiSelectionContext.Snapshot(id,selections,plan.businessScope(),budgets(plan),plan.worldInput(),plan.worldAssistantInput(),plan.writingInput())),Timestamp.from(expires));
            return new View(id,expires,List.copyOf(steps),steps.stream().mapToLong(QuoteStep::estimatedCredits).sum());
        });
    }
    public AiSelectionContext.Snapshot accept(User user,String suppliedTarget,JsonNode body,String quoteId,String requestKey) {
        String target=canonicalTarget(suppliedTarget);
        if(requestKey!=null&&(requestKey.isBlank()||requestKey.length()>128))throw new ApiStatusException(HttpStatus.BAD_REQUEST,"AI_REQUEST_KEY_INVALID");
        config.ensureInitialized();
        if(quoteId==null||quoteId.isBlank()) {
            return tx.execute(s->{
                jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
                String receipt=requestKey==null?null:UUID.nameUUIDFromBytes((user.getId()+"\n"+target+"\n"+requestKey).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
                if(receipt!=null) {
                    var prior=jdbc.queryForList("select input_hash,selections_json from ai_model_quotes where id=? and user_id=?",receipt,AiModelConfigurationService.bytes(user.getId()));
                    if(!prior.isEmpty()) {
                        if(!inputHash(target,body).equals(prior.getFirst().get("input_hash")))throw AiModelConfigurationService.error("AI_QUOTE_INPUT_CHANGED");
                        return config.readSnapshot((String)prior.getFirst().get("selections_json"));
                    }
                }
                requireNoUnresolvedOperation(user,target,body,receipt==null?"":receipt);
                var plan=planner.plan(user,target,body);var values=new LinkedHashMap<String,AiModelSelection>();
                for(var step:plan.steps()) {
                    var f=config.feature(step.featureKey(),user);
                    if(f.options().isEmpty())throw AiModelConfigurationService.error("AI_FEATURE_UNCONFIGURED");
                    if(f.billingEnabled())throw new ApiStatusException(HttpStatus.PRECONDITION_REQUIRED,"AI_QUOTE_REQUIRED");
                    var selected=f.options().stream().filter(v->v.optionId().equals(f.defaultOptionId())&&!f.unavailableOptions().contains(v.optionId())).findFirst().orElseThrow(()->AiModelConfigurationService.error("AI_OPTION_UNAVAILABLE"));
                    values.put(step.featureKey(),selected);
                }
                var snapshot=new AiSelectionContext.Snapshot(receipt==null?UUID.randomUUID().toString():receipt,values,plan.businessScope(),budgets(plan),plan.worldInput(),plan.worldAssistantInput(),plan.writingInput());
                if(receipt!=null)jdbc.update("insert into ai_model_quotes(id,user_id,input_hash,target_path,request_json,business_hash,configuration_revision,selections_json,accepted_request_key,expires_at) values(?,?,?,?,?,?,?,?,?,?)",
                        receipt,AiModelConfigurationService.bytes(user.getId()),inputHash(target,body),target,config.write(body),plan.businessHash(),config.revision(),config.write(snapshot),requestKey,Timestamp.from(Instant.now()));
                return snapshot;
            });
        }
        return tx.execute(s->{
            var rows=jdbc.queryForList("select * from ai_model_quotes where id=? and user_id=? for update",quoteId,AiModelConfigurationService.bytes(user.getId()));
            if(rows.isEmpty())throw AiModelConfigurationService.error("AI_QUOTE_INVALID");
            var q=rows.getFirst();
            if(!inputHash(target,body).equals(q.get("input_hash")))throw AiModelConfigurationService.error("AI_QUOTE_INPUT_CHANGED");
            String binding=requestKey==null?quoteId:requestKey;
            String accepted=(String)q.get("accepted_request_key");
            if(accepted!=null) {
                if(!accepted.equals(binding))throw AiModelConfigurationService.error("AI_QUOTE_ALREADY_USED");
                return config.readSnapshot((String)q.get("selections_json"));
            }
            var plan=planner.plan(user,target,body);
            long revision=jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
            requireNoUnresolvedOperation(user,target,body,quoteId);
            if(!((Timestamp)q.get("expires_at")).toInstant().isAfter(Instant.now()))throw AiModelConfigurationService.error("AI_QUOTE_EXPIRED");
            if(revision!=((Number)q.get("configuration_revision")).longValue()||!plan.businessHash().equals(q.get("business_hash")))throw AiModelConfigurationService.error("AI_QUOTE_INPUT_CHANGED");
            jdbc.update("update ai_model_quotes set accepted_request_key=? where id=?",binding,quoteId);
            return config.readSnapshot((String)q.get("selections_json"));
        });
    }
    private String canonicalTarget(String target) {
        return target.replaceFirst("(/v1/worlds/[^/]+/generation/[^/?]+)/retry(?=\\?|$)","$1");
    }
    private String inputHash(String target,JsonNode body) {return AiActionPlanner.hash(target+"\n"+config.write(body));}
    private void requireNoUnresolvedOperation(User user,String target,JsonNode body,String ownQuote) {
        var statuses=jdbc.query("select r.status from ai_model_quotes q join ai_credit_reservations r on r.request_identity=q.id and r.user_id=q.user_id where q.user_id=? and q.target_path=? and q.input_hash=? and q.id<>? and r.status in ('RESERVED','RESULT_READY','RECONCILIATION_REQUIRED') order by r.created_at desc limit 1",
                (rs,i)->rs.getString(1),AiModelConfigurationService.bytes(user.getId()),target,inputHash(target,body),ownQuote);
        if(statuses.isEmpty())return;
        String status=statuses.getFirst();
        if(status.equals("RESULT_READY"))throw new AiPaymentRequiredException();
        throw new ApiStatusException(HttpStatus.CONFLICT,status.equals("RESERVED")?"AI_REQUEST_IN_PROGRESS":"AI_RESULT_RECONCILIATION_REQUIRED");
    }
    private Map<String,Integer> budgets(AiActionPlanner.Plan plan) {
        Map<String,Integer> values=new LinkedHashMap<>();for(var step:plan.steps())values.put(step.featureKey(),step.estimatedOutput());return values;
    }
    private static int maximumCalls(String value){if(value!=null&&value.matches("UP_TO_[0-9]+"))return Integer.parseInt(value.substring(6));return 1;}
    /** A durable receipt uses the existing quote snapshot store for G2's independently scheduled task. */
    public void freezeTaskSnapshot(User user,UUID taskId) {
        var snapshot=AiSelectionContext.current();if(snapshot==null)return;
        String id=taskId.toString(),target="internal:g2:"+id;
        jdbc.update("insert into ai_model_quotes(id,user_id,input_hash,target_path,request_json,business_hash,configuration_revision,selections_json,accepted_request_key,expires_at) values(?,?,?,?,?,?,?,?,?,?)",
                id,AiModelConfigurationService.bytes(user.getId()),AiActionPlanner.hash(target),target,"{}",AiActionPlanner.hash(id),config.revision(),config.write(snapshot),id,Timestamp.from(Instant.now()));
    }
    public AiSelectionContext.Snapshot taskSnapshot(UUID taskId) {
        return jdbc.query("select selections_json from ai_model_quotes where id=? and target_path=?",(rs,i)->config.readSnapshot(rs.getString(1)),taskId.toString(),"internal:g2:"+taskId).stream().findFirst().orElse(null);
    }

    public List<Pending> pending(User user) {
        var rows=jdbc.queryForList("select q.* from ai_model_quotes q where q.user_id=? and exists(select 1 from ai_credit_reservations r where r.user_id=q.user_id and r.request_identity=q.id and r.status='RESULT_READY') order by q.expires_at desc",AiModelConfigurationService.bytes(user.getId()));
        List<Pending> result=new ArrayList<>();
        for(var row:rows) {
            String id=(String)row.get("id");
            var calls=jdbc.queryForList("select operation_id,reference_type,reference_id,selection_json from ai_credit_reservations where user_id=? and request_identity=? and status='RESULT_READY' order by updated_at desc",AiModelConfigurationService.bytes(user.getId()),id);
            if(calls.isEmpty())continue;
            var call=calls.getFirst();var selected=config.read((String)call.get("selection_json"),AiModelSelection.class);
            String target=(String)row.get("target_path"),quoteId=id;JsonNode body;
            try{body=json.readTree((String)row.get("request_json"));}catch(Exception e){throw new IllegalStateException(e);}
            if(call.get("operation_id") instanceof byte[] operation) {
                var bytes=java.nio.ByteBuffer.wrap(operation);target="/v1/ai-operations/"+new UUID(bytes.getLong(),bytes.getLong())+"/retry";body=json.createObjectNode();quoteId=null;
            } else if("G2_EVALUATION".equals(call.get("reference_type"))) {
                target="/v1/g2-evaluations/samples/"+UUID.fromString((String)call.get("reference_id"))+"/retry";body=json.createObjectNode();quoteId=null;
            } else if("G1_WORKFLOW".equals(call.get("reference_type"))) {
                var scopes=jdbc.query("select scope_id from async_jobs where id=? and user_id=?",(rs,i)->rs.getBytes(1),AiModelConfigurationService.bytes(UUID.fromString((String)call.get("reference_id"))),AiModelConfigurationService.bytes(user.getId()));
                if(scopes.isEmpty())continue;
                var bytes=java.nio.ByteBuffer.wrap(scopes.getFirst());target="/v1/creation-workflows/"+new UUID(bytes.getLong(),bytes.getLong())+"/retry";body=json.createObjectNode();quoteId=null;
            }
            result.add(new Pending(id,target,body,(String)row.get("accepted_request_key"),quoteId,selected.label()));
        }
        return List.copyOf(result);
    }
}
