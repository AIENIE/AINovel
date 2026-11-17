package com.ainovel.app.modelconfig;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.model.V2ModelRegistry;
import com.ainovel.app.v2.repo.V2ModelRegistryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class AiModelConfigurationService {
    public record Thinking(boolean declared,boolean supportsEnable,boolean supportsDisable,
            List<ThinkingOption> options,Boolean defaultEnabled,String defaultEffort) {
        public static Thinking unknown() { return new Thinking(false,false,false,List.of(),null,null); }
    }
    public record ThinkingOption(String effort,String label) {}
    public record Specialized(String capability,String model,String source) {}
    public record ChatCapabilities(boolean budgetDeclared,boolean budgetAvailable,boolean toolsDeclared,boolean supportsTools,
            long maxPromptTokens,long maxOutputTokens,String budgetUnavailableReason,
            boolean budgetToolsDeclared,boolean budgetToolsAvailable,String budgetToolsUnavailableReason) {
        public static ChatCapabilities unknown() {return new ChatCapabilities(false,false,false,false,0,0,null,false,false,null);}
        public static ChatCapabilities from(fireflychat.ai.v1.ModelChatCapabilities caps) {
            return new ChatCapabilities(caps.getBudgetDeclared(),caps.getBudgetAvailable(),caps.getToolsDeclared(),caps.getSupportsTools(),
                    caps.getMaxPromptTokens(),caps.getMaxOutputTokens(),caps.getBudgetUnavailableReason(),caps.getBudgetToolsDeclared(),caps.getBudgetToolsAvailable(),caps.getBudgetToolsUnavailableReason());
        }
    }
    public record CatalogModel(String id,String modelKey,String displayName,String provider,boolean available,
            boolean supportsStreaming,boolean supportsImageInput,Thinking thinking,Instant syncedAt,ChatCapabilities chatCapabilities) {
        public CatalogModel(String id,String modelKey,String displayName,String provider,boolean available,
                boolean supportsStreaming,boolean supportsImageInput,Thinking thinking,Instant syncedAt) {
            this(id,modelKey,displayName,provider,available,supportsStreaming,supportsImageInput,thinking,syncedAt,ChatCapabilities.unknown());
        }
    }
    public record OptionInput(String id,String modelKey,String label,String thinkingMode,String thinkingEffort,
            String pricingMode,Long fixedCredits,BigDecimal inputPer1k,BigDecimal outputPer1k) {}
    public record FeatureUpdate(long revision,boolean billingEnabled,String defaultOptionId,List<OptionInput> options,
                                Map<String,Integer> outputTokenLimits) {
        public FeatureUpdate(long revision,boolean billingEnabled,String defaultOptionId,List<OptionInput> options) {
            this(revision,billingEnabled,defaultOptionId,options,Map.of());
        }
    }
    public record FeatureView(AiFeatures.Definition definition,long revision,boolean billingEnabled,
            String defaultOptionId,String preferredOptionId,boolean preferenceInvalid,List<AiModelSelection> options,
            List<String> unavailableOptions,Map<String,Integer> outputTokenLimits) {}
    public record RoutingView(AiFeatures.Definition definition,long revision,boolean billingEnabled,String defaultOptionId,
            List<AiModelSelection> options,List<String> unavailableOptions,Map<String,Integer> outputTokenLimits,
            List<AiFeatures.Definition> operations,List<LegacyDefault> legacyDefaults) {}
    public record LegacyDefault(AiFeatures.Definition operation,AiModelSelection option) {}

    private record Route(String key, boolean billed, long revision, String defaultId, Map<String,Integer> outputLimits) {}
    private record Price(boolean billed, String mode, Long fixed, BigDecimal input, BigDecimal output) {
        static Price of(AiModelSelection option) {
            if (!option.billingEnabled()) return new Price(false, null, null, null, null);
            if ("FIXED".equals(option.pricingMode())) return new Price(true, "FIXED", option.fixedCredits(), null, null);
            return new Price(true, "TOKENS", null, option.inputPer1k().stripTrailingZeros(), option.outputPer1k().stripTrailingZeros());
        }
    }
    private record PlanIdentity(String model, String mode, String effort, Price price) {
        static PlanIdentity of(AiModelSelection option) {
            return new PlanIdentity(option.modelKey(), option.thinkingMode(), option.thinkingEffort(), Price.of(option));
        }
    }
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.01");
    private final V2ModelRegistryRepository models;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final AiModelPolicy bootstrap;
    private final AiGatewayGrpcClient gateway;
    public AiModelConfigurationService(V2ModelRegistryRepository models,JdbcTemplate jdbc,ObjectMapper json,
            TransactionTemplate tx,AiModelPolicy bootstrap,AiGatewayGrpcClient gateway) {
        this.models=models;this.jdbc=jdbc;this.json=json;this.tx=tx;this.bootstrap=bootstrap;this.gateway=gateway;
    }
    public void ensureInitialized() {
        tx.executeWithoutResult(status->{
            jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
            V2ModelRegistry model=models.findByModelKey(bootstrap.modelKey()).orElseGet(()->{
                var m=new V2ModelRegistry();m.setModelKey(bootstrap.modelKey());m.setDisplayName(bootstrap.displayName());m.setProvider("未同步");
                m.setSupportsStreaming(true);m.setAvailable(jdbc.queryForObject("select count(*) from ai_model_catalog_state where id=1 and last_success_at is not null",Integer.class)==0);return models.saveAndFlush(m);
            });
            var existing = new HashSet<>(jdbc.queryForList("select task_type from task_model_routing", String.class));
            boolean changed = false;
            for (var role : AiModelResponsibilities.all()) {
                if (!existing.contains(role.key())) changed |= initializeRole(role, existing, model);
            }
            if (changed) advanceCatalogRevision();
        });
    }

    private boolean initializeRole(AiModelResponsibilities.Role role, Set<String> existing, V2ModelRegistry model) {
        var defaults = legacyDefaults(role, existing);
        if (defaults.isEmpty()) {
            if (AiModelResponsibilities.AGENT_CONTROL.equals(role.key())) return false;
            String id = UUID.randomUUID().toString();
            var selection = new AiModelSelection(role.key(), id, model.getModelKey(), model.getDisplayName(), "DEFAULT", null,
                    model.isSupportsStreaming(), true, "TOKENS", null, DEFAULT_RATE, DEFAULT_RATE, 0);
            insertRoute(role.key(), model.getId(), true, 0, id, Map.of());
            insertOption(selection, model.getId(), 0);
            return true;
        }
        var primary = defaults.getFirst().option();
        Price price = Price.of(primary);
        if (defaults.stream().anyMatch(entry -> !price.equals(Price.of(entry.option())))) return false;

        String defaultId = sharedOptionId(role.key(), primary.optionId());
        var distinct = new LinkedHashMap<PlanIdentity,AiModelSelection>();
        distinct.put(PlanIdentity.of(primary), primary);
        if (primary.billingEnabled()) {
            for (var operation : role.operations()) {
                if (!existing.contains(operation.key())) continue;
                for (var option : storedOptions(operation.key())) distinct.putIfAbsent(PlanIdentity.of(option), option);
            }
        }
        insertRoute(role.key(), modelId(primary.modelKey()), primary.billingEnabled(), 0, defaultId, Map.of());
        var labels = new HashSet<String>();
        int index = 0;
        for (var option : distinct.values()) {
            String id = sharedOptionId(role.key(), option.optionId());
            String label = option.label();
            if (!labels.add(label)) {
                int suffix = index + 1;
                do { label = option.label().substring(0, Math.min(180, option.label().length())) + " · " + suffix++; }
                while (!labels.add(label));
            }
            insertOption(new AiModelSelection(role.key(), id, option.modelKey(), label, option.thinkingMode(),
                    option.thinkingEffort(), option.streaming(), primary.billingEnabled(), option.pricingMode(),
                    option.fixedCredits(), option.inputPer1k(), option.outputPer1k(), 0), modelId(option.modelKey()), index++);
        }
        return true;
    }

    private List<LegacyDefault> legacyDefaults(AiModelResponsibilities.Role role, Set<String> existing) {
        var result = new ArrayList<LegacyDefault>();
        for (var operation : role.operations()) {
            if (!existing.contains(operation.key())) continue;
            var route = storedRoute(operation.key());
            if (route == null || route.defaultId() == null) continue;
            storedOptions(route.key()).stream().filter(option -> route.defaultId().equals(option.optionId())).findFirst()
                    .ifPresent(option -> result.add(new LegacyDefault(operation, project(option, operation.key(), route, option.streaming()))));
        }
        return result;
    }

    private static String sharedOptionId(String roleKey, String legacyId) {
        return UUID.nameUUIDFromBytes((roleKey + "\n" + legacyId).getBytes(StandardCharsets.UTF_8)).toString();
    }
    public Map<String,Object> catalog() {
        ensureInitialized();
        var row=jdbc.queryForMap("select revision,last_attempt_at,last_success_at,last_error from ai_model_catalog_state where id=1");
        var state=new LinkedHashMap<String,Object>();
        for(String key:List.of("revision","last_attempt_at","last_success_at","last_error"))state.put(key,row.get(key));
        state.put("models",models.findByOrderByPriorityDesc().stream().map(this::catalogModel).toList());
        state.put("specialized",List.of(new Specialized("Embedding","跟随 ai-service 默认","旧资料检索，保留空模型键语义"),
                new Specialized("Embedding",com.ainovel.app.material.evidence.MaterialNativeClient.profile("qwen-standard-1024-cp-v1").model(),"标准 1024 维选型配置"),
                new Specialized("Embedding",com.ainovel.app.material.evidence.MaterialNativeClient.profile("qwen-flash-1024-cp-v1").model(),"Flash 1024 维选型配置"),
                new Specialized("Rerank",AiGatewayGrpcClient.RETRIEVAL_RERANK_MODEL,"保留原业务开关与预算"),
                new Specialized("结构化核验",AiGatewayGrpcClient.RETRIEVAL_STRUCTURED_MODEL,"非思考、JSON Schema、原预算约束")));
        return state;
    }
    public Map<String,Object> sync() {
        ensureInitialized();
        List<CatalogModel> incoming;
        try {
            incoming=gateway.listCallerModels();
            Set<String> keys=new HashSet<>();
            for(var item:incoming) if(item.modelKey()==null||item.modelKey().isBlank()||!keys.add(item.modelKey())
                    ||item.modelKey().length()>100||item.displayName().length()>200||item.provider().length()>50) throw error("AI_CATALOG_INVALID");
        } catch(RuntimeException ex) {
            tx.executeWithoutResult(s->jdbc.update("update ai_model_catalog_state set last_attempt_at=?,last_error=? where id=1",Timestamp.from(Instant.now()),"AI_CATALOG_SYNC_FAILED"));
            throw new ApiStatusException(HttpStatus.BAD_GATEWAY,"AI_CATALOG_SYNC_FAILED");
        }
        tx.executeWithoutResult(s->{
            jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
            var all=models.findAll();all.forEach(m->m.setAvailable(false));
            Instant now=Instant.now();
            for(var entry:incoming) {
                var m=all.stream().filter(v->v.getModelKey().equals(entry.modelKey())).findFirst().orElseGet(()->{var v=new V2ModelRegistry();v.setModelKey(entry.modelKey());all.add(v);return v;});
                m.setDisplayName(entry.displayName());m.setProvider(entry.provider());m.setAvailable(true);
                m.setGatewayModelId(Long.valueOf(entry.id()));m.setSupportsStreaming(entry.supportsStreaming());
                m.setSupportsImageInput(entry.supportsImageInput());m.setThinkingJson(write(entry.thinking()));m.setLastSyncedAt(now);
                var capabilities=json.createObjectNode();
                if(m.getCapabilitiesJson()!=null) {
                    try {
                        var old=json.readTree(m.getCapabilitiesJson());
                        if(old.isObject())capabilities.setAll((com.fasterxml.jackson.databind.node.ObjectNode)old);
                        else capabilities.put("legacyCapabilitiesJson",m.getCapabilitiesJson());
                    }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){capabilities.put("legacyCapabilitiesJson",m.getCapabilitiesJson());}
                }
                capabilities.set("gatewayChat",json.valueToTree(entry.chatCapabilities()==null?ChatCapabilities.unknown():entry.chatCapabilities()));
                m.setCapabilitiesJson(write(capabilities));
            }
            models.saveAllAndFlush(all);
            jdbc.update("update ai_model_catalog_state set revision=revision+1,last_attempt_at=?,last_success_at=?,last_error=null where id=1",Timestamp.from(now),Timestamp.from(now));
        });
        return catalog();
    }
    public List<FeatureView> features(User user) {
        ensureInitialized();
        return AiFeatures.all().stream().map(f->feature(f.key(),user)).toList();
    }

    public List<RoutingView> routing() {
        ensureInitialized();
        return AiModelResponsibilities.all().stream().map(this::routing).toList();
    }

    private RoutingView routing(AiModelResponsibilities.Role role) {
        var route = storedRoute(role.key());
        var view = view(role.definition(), route, null);
        var defaults = route == null ? legacyDefaults(role, new HashSet<>(jdbc.queryForList(
                "select task_type from task_model_routing", String.class))) : List.<LegacyDefault>of();
        return new RoutingView(view.definition(), view.revision(), view.billingEnabled(), view.defaultOptionId(),
                view.options(), view.unavailableOptions(), view.outputTokenLimits(), role.operations(), defaults);
    }

    public FeatureView feature(String key,User user) {
        var definition = AiFeatures.require(key);
        var role = AiModelResponsibilities.forOperation(key);
        var shared = role == null ? null : storedRoute(role.key());
        return view(definition, shared != null ? shared : storedRoute(key), user);
    }

    private FeatureView view(AiFeatures.Definition definition, Route route, User user) {
        if (route == null) return new FeatureView(definition, 0, true, null, null, false, List.of(), List.of(), Map.of());
        List<String> unavailable = new ArrayList<>();
        var options = storedOptions(route.key()).stream().map(option -> {
            var model = catalogModel(models.findByModelKey(option.modelKey()).orElseThrow());
            try {
                validateThinking(model.thinking(), option.thinkingMode(), option.thinkingEffort());
                validateRoleCapabilities(route.key(), model);
                if (!model.available()) unavailable.add(option.optionId());
            } catch (ApiStatusException invalid) {
                unavailable.add(option.optionId());
            }
            return project(option, definition.key(), route, model.supportsStreaming());
        }).toList();
        String historicalPreference = !route.billed() || user == null ? null : jdbc.query(
                "select preferred_option_id from user_model_preferences where user_id=? and task_type=?",
                (rs, i) -> rs.getString(1), bytes(user.getId()), definition.key()).stream().filter(Objects::nonNull).findFirst().orElse(null);
        String preferred = optionId(definition.key(), historicalPreference, options);
        boolean invalid = preferred != null && (options.stream().noneMatch(o -> o.optionId().equals(preferred)) || unavailable.contains(preferred));
        return new FeatureView(definition, route.revision(), route.billed(), route.defaultId(), preferred, invalid,
                options, List.copyOf(unavailable), route.outputLimits());
    }

    public String optionId(FeatureView feature, String id) {
        return optionId(feature.definition().key(), id, feature.options());
    }

    private String optionId(String operation, String id, List<AiModelSelection> options) {
        if (id == null || options.stream().anyMatch(option -> option.optionId().equals(id))) return id;
        var rows = jdbc.query("select settings_json from ai_model_options where id=? and task_type=? and active=true",
                (rs, i) -> read(rs.getString(1), AiModelSelection.class), id, operation);
        if (rows.isEmpty()) return id;
        var legacy = rows.getFirst();
        var identity = PlanIdentity.of(legacy);
        return options.stream().filter(option -> identity.equals(PlanIdentity.of(option)))
                .min(Comparator.comparing(option -> !option.label().equals(legacy.label())))
                .map(AiModelSelection::optionId).orElse(id);
    }

    private static AiModelSelection project(AiModelSelection option, String operation, Route route, boolean streaming) {
        return new AiModelSelection(operation, option.optionId(), option.modelKey(), option.label(), option.thinkingMode(),
                option.thinkingEffort(), streaming, route.billed(), option.pricingMode(), option.fixedCredits(),
                option.inputPer1k(), option.outputPer1k(), route.revision());
    }

    private Route storedRoute(String key) {
        return jdbc.query("select task_type,billing_enabled,configuration_revision,default_option_id,config_json "
                        + "from task_model_routing where task_type=?", (rs,i) -> storedRoute(rs), key).stream().findFirst().orElse(null);
    }

    private Route storedRoute(ResultSet rs) throws SQLException {
        String key = rs.getString("task_type");
        var limits = Map.<String,Integer>of();
        if (AiModelResponsibilities.byKey(key) != null) {
            String raw = rs.getString("config_json");
            if (raw != null) {
                try {
                    limits = json.readValue(raw, new TypeReference<Map<String,Integer>>() {});
                } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) {
                    throw new IllegalStateException("Unreadable model output limits", invalid);
                }
            }
        }
        return new Route(key, rs.getBoolean("billing_enabled"), rs.getLong("configuration_revision"),
                rs.getString("default_option_id"), limits);
    }

    private List<AiModelSelection> storedOptions(String key) {
        return jdbc.query("select settings_json from ai_model_options where task_type=? and active=true order by position_index,id",
                (rs, i) -> read(rs.getString(1), AiModelSelection.class), key);
    }

    private UUID modelId(String modelKey) {
        return models.findByModelKey(modelKey).orElseThrow().getId();
    }

    private void insertRoute(String key, UUID modelId, boolean billed, long revision, String defaultId, Map<String,Integer> limits) {
        var now = Timestamp.from(Instant.now());
        jdbc.update("insert into task_model_routing(id,task_type,recommended_model_id,routing_strategy,config_json,"
                        + "created_at,updated_at,billing_enabled,configuration_revision,default_option_id) "
                        + "values(?,?,?,'fixed',?,?,?,?,?,?)",
                bytes(UUID.randomUUID()), key, bytes(modelId), write(limits), now, now, billed, revision, defaultId);
    }

    private void advanceCatalogRevision() {
        jdbc.update("update ai_model_catalog_state set revision=revision+1 where id=1");
    }

    public RoutingView update(String key,FeatureUpdate request) {
        var role = AiModelResponsibilities.byKey(key);
        if (role == null) throw error("AI_CONFIGURATION_CHANGED");
        ensureInitialized();
        return tx.execute(s->{
            jdbc.queryForObject("select revision from ai_model_catalog_state where id=1 for update",Long.class);
            var prior = storedRoute(key);
            long revision = prior == null ? 0 : prior.revision();
            if(revision!=request.revision()) throw error("AI_CONFIGURATION_CHANGED");
            if(request.options()==null||request.options().isEmpty()||(!request.billingEnabled()&&request.options().size()!=1)) throw error("AI_OPTIONS_INVALID");
            Set<String> ids=new HashSet<>(),labels=new HashSet<>();List<AiModelSelection> selections=new ArrayList<>();
            for(var option:request.options()) {
                var m=models.findByModelKey(option.modelKey()).orElseThrow(()->error("AI_MODEL_UNAVAILABLE"));
                if(!m.isAvailable()) throw error("AI_MODEL_UNAVAILABLE");
                if(option.id()==null||!option.id().matches("[0-9a-fA-F-]{36}")||!ids.add(option.id())||option.label()==null||option.label().isBlank()||option.label().length()>200||!labels.add(option.label().strip())) throw error("AI_OPTIONS_INVALID");
                var owner=jdbc.query("select task_type from ai_model_options where id=?",(rs,i)->rs.getString(1),option.id());
                if(!owner.isEmpty()&&!owner.getFirst().equals(key)) throw error("AI_OPTIONS_INVALID");
                var catalog = catalogModel(m);
                validateThinking(catalog.thinking(),option.thinkingMode(),option.thinkingEffort());
                validateRoleCapabilities(key, catalog);
                validatePrice(request.billingEnabled(),option);
                selections.add(new AiModelSelection(key,option.id(),m.getModelKey(),option.label().strip(),option.thinkingMode(),option.thinkingEffort(),m.isSupportsStreaming(),request.billingEnabled(),request.billingEnabled()?option.pricingMode():"TOKENS",option.fixedCredits(),option.inputPer1k(),option.outputPer1k(),revision+1));
            }
            if(!ids.contains(request.defaultOptionId())) throw error("AI_DEFAULT_OPTION_INVALID");
            var limits = request.outputTokenLimits() == null ? Map.<String,Integer>of() : request.outputTokenLimits();
            if (!ids.containsAll(limits.keySet()) || limits.values().stream().anyMatch(limit -> limit == null || limit <= 0)) {
                throw error("AI_OUTPUT_LIMIT_INVALID");
            }
            var primary=selections.stream().filter(v->v.optionId().equals(request.defaultOptionId())).findFirst().orElseThrow();
            if (prior == null) insertRoute(key, modelId(primary.modelKey()), request.billingEnabled(), revision, request.defaultOptionId(), limits);
            jdbc.update("update ai_model_options set active=false where task_type=?",key);
            int index=0;for(var selected:selections) {
                jdbc.update("delete from ai_model_options where id=? and task_type=?",selected.optionId(),key);
                insertOption(selected,modelId(selected.modelKey()),index++);
            }
            jdbc.update("update task_model_routing set billing_enabled=?,configuration_revision=configuration_revision+1,default_option_id=?,recommended_model_id=?,fallback_model_id=null,config_json=?,updated_at=? where task_type=?",request.billingEnabled(),request.defaultOptionId(),bytes(modelId(primary.modelKey())),write(limits),Timestamp.from(Instant.now()),key);
            advanceCatalogRevision();
            return routing(role);
        });
    }

    private static void validateRoleCapabilities(String key, CatalogModel model) {
        if (AiModelResponsibilities.AGENT_CONTROL.equals(key)
                && (!model.chatCapabilities().budgetToolsDeclared() || !model.chatCapabilities().budgetToolsAvailable())) {
            throw error("AI_NATIVE_BUDGET_UNAVAILABLE");
        }
    }

    public FeatureUpdate parseUpdate(com.fasterxml.jackson.databind.JsonNode payload) {
        try {
            if(payload.path("billingEnabled").asBoolean()&&payload.path("options").isArray())for(var option:payload.path("options"))
                if("FIXED".equals(option.path("pricingMode").asText())&&!option.path("fixedCredits").isIntegralNumber())throw new IllegalArgumentException();
            var limits = payload.path("outputTokenLimits");
            if (!limits.isMissingNode() && !limits.isNull()) {
                if (!limits.isObject()) throw new IllegalArgumentException();
                for (var limit : limits) if (!limit.isIntegralNumber() || !limit.canConvertToInt()) throw new IllegalArgumentException();
            }
            return json.treeToValue(payload,FeatureUpdate.class);
        } catch(Exception e){throw new ApiStatusException(HttpStatus.BAD_REQUEST,"AI_OPTIONS_INVALID");}
    }
    public void preference(User user,String key,String optionId) {
        if(AiFeatures.require(key).retired()) throw error("AI_FEATURE_RETIRED");
        ensureInitialized();tx.executeWithoutResult(s->{
            var feature=feature(key,user);
            String desired = optionId(feature, optionId);
            if(!feature.billingEnabled()) throw error("AI_FEATURE_IS_FREE");
            if(desired!=null&&(feature.unavailableOptions().contains(desired)||feature.options().stream().noneMatch(o->o.optionId().equals(desired)))) throw error("AI_OPTION_UNAVAILABLE");
            var ids=jdbc.query("select id from user_model_preferences where user_id=? and task_type=?",(rs,i)->rs.getBytes(1),bytes(user.getId()),key);
            if(ids.isEmpty()) jdbc.update("insert into user_model_preferences(id,user_id,task_type,preferred_option_id,created_at,updated_at) values(?,?,?,?,?,?)",bytes(UUID.randomUUID()),bytes(user.getId()),key,desired,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
            else jdbc.update("update user_model_preferences set preferred_option_id=?,updated_at=? where user_id=? and task_type=?",desired,Timestamp.from(Instant.now()),bytes(user.getId()),key);
        });
    }
    public long revision() {return jdbc.queryForObject("select revision from ai_model_catalog_state where id=1",Long.class);}
    public Map<String,Object> usageSummary(User user) {
        var row=jdbc.queryForMap("select count(*) as totalCalls,coalesce(sum(prompt_tokens),0) as totalInputTokens,coalesce(sum(completion_tokens),0) as totalOutputTokens,coalesce(sum(settled_amount),0) as totalCost from ai_credit_reservations where user_id=?",bytes(user.getId()));
        Map<String,Object> result=new LinkedHashMap<>();for(String key:List.of("totalCalls","totalInputTokens","totalOutputTokens","totalCost"))result.put(key,row.get(key));return result;
    }
    public List<Map<String,Object>> usageDetails(User user,int limit) {
        return jdbc.query("select id,selection_json,actual_model_key,status,prompt_tokens,completion_tokens,settled_amount,reserved_amount,created_at from ai_credit_reservations where user_id=? order by created_at desc,id desc limit ?",(rs,index)->{
            String raw=rs.getString("selection_json");AiModelSelection option=raw==null?null:read(raw,AiModelSelection.class);
            Map<String,Object> result=new LinkedHashMap<>();byte[] id=rs.getBytes("id");var buffer=ByteBuffer.wrap(id);
            result.put("id",new UUID(buffer.getLong(),buffer.getLong()).toString());
            result.put("taskType",option==null?"legacy":option.featureKey());
            result.put("definition",option==null?new AiFeatures.Definition("legacy","tools","历史 AI 请求","歷史 AI 請求","Historical AI request"):AiFeatures.require(option.featureKey()));
            result.put("modelLabel",option==null?null:option.label());result.put("modelKey",rs.getString("actual_model_key"));
            result.put("modelId",option==null?null:models.findByModelKey(option.modelKey()).map(m->m.getId().toString()).orElse(null));
            result.put("optionId",option==null?null:option.optionId());result.put("selection",option);
            result.put("inputTokens",rs.getLong("prompt_tokens"));result.put("outputTokens",rs.getLong("completion_tokens"));
            result.put("costEstimate",rs.getLong("settled_amount"));result.put("reservedCredits",rs.getLong("reserved_amount"));
            result.put("billingStatus",rs.getString("status"));result.put("createdAt",rs.getTimestamp("created_at").toInstant());return result;
        },bytes(user.getId()),Math.max(1,Math.min(limit,500)));
    }
    public Map<String,Object> asMap(Object value) {return json.convertValue(value,new TypeReference<Map<String,Object>>(){});}
    public List<com.ainovel.app.ai.dto.AiModelDto> publicModels() {
        ensureInitialized();
        return models.findByOrderByPriorityDesc().stream().filter(V2ModelRegistry::isAvailable).map(m->
                new com.ainovel.app.ai.dto.AiModelDto(m.getModelKey(),m.getModelKey(),m.getDisplayName(),"text",0,0,m.getProvider(),true,m.getSupportsImageInput(),m.isSupportsStreaming())).toList();
    }
    public boolean available(String model) {return models.findByModelKey(model).map(V2ModelRegistry::isAvailable).orElse(false);}
    public ChatCapabilities nativeCapabilities(String model) {
        return models.findByModelKey(model).filter(V2ModelRegistry::isAvailable).map(m->catalogModel(m).chatCapabilities()).orElse(ChatCapabilities.unknown());
    }
    public CatalogModel catalogModel(V2ModelRegistry m) {
        ChatCapabilities chat=ChatCapabilities.unknown();
        if(m.getCapabilitiesJson()!=null)try {
            var node=json.readTree(m.getCapabilitiesJson()).get("gatewayChat");
            if(node!=null && node.isObject())chat=json.treeToValue(node,ChatCapabilities.class);
        }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalStateException("AI_MODEL_CAPABILITIES_INVALID",invalid); }
        return new CatalogModel(m.getGatewayModelId()==null?null:m.getGatewayModelId().toString(),m.getModelKey(),m.getDisplayName(),m.getProvider(),m.isAvailable(),m.isSupportsStreaming(),m.getSupportsImageInput(),m.getThinkingJson()==null?Thinking.unknown():read(m.getThinkingJson(),Thinking.class),m.getLastSyncedAt(),chat);
    }
    private void insertOption(AiModelSelection option,UUID modelId,int index) {jdbc.update("insert into ai_model_options(id,task_type,model_id,settings_json,active,position_index) values(?,?,?,?,true,?)",option.optionId(),option.featureKey(),bytes(modelId),write(option),index);}
    public static void validateThinking(Thinking thinking,String mode,String effort) {
        if("DEFAULT".equals(mode)&&effort==null) return;
        if(thinking==null||!thinking.declared()) throw error("AI_THINKING_UNSUPPORTED");
        if("OFF".equals(mode)&&effort==null&&thinking.supportsDisable()) return;
        if("ON".equals(mode)&&thinking.supportsEnable()&&(effort==null||thinking.options().stream().anyMatch(o->o.effort().equals(effort)))) return;
        throw error("AI_THINKING_UNSUPPORTED");
    }
    private static void validatePrice(boolean billing,OptionInput o) {
        if(!billing) return;
        if("FIXED".equals(o.pricingMode())&&o.fixedCredits()!=null&&o.fixedCredits()>0) return;
        if("TOKENS".equals(o.pricingMode())&&validRate(o.inputPer1k())&&validRate(o.outputPer1k())&&o.inputPer1k().add(o.outputPer1k()).signum()>0) return;
        throw error("AI_PRICE_INVALID");
    }
    private static boolean validRate(BigDecimal n) {return n!=null&&n.signum()>=0&&n.scale()<=6;}
    public String write(Object v) {try{return json.writeValueAsString(v);}catch(Exception e){throw new IllegalStateException(e);}}
    public <T>T read(String v,Class<T> type) {try{return json.readValue(v,type);}catch(Exception e){throw new IllegalStateException("AI configuration snapshot unreadable",e);}}
    public AiSelectionContext.Snapshot readSnapshot(String v) {return v==null?null:read(v,AiSelectionContext.Snapshot.class);}
    public static byte[] bytes(UUID id) {return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
    static ApiStatusException error(String code) {return new ApiStatusException(HttpStatus.CONFLICT,code);}
}
