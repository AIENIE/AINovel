package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Retires the original operation keys from live model configuration. */
public class V51__consolidate_model_responsibilities extends BaseJavaMigration {
    private static final Map<String,List<String>> OLD_KEYS = Map.ofEntries(
        Map.entry("role.conception", List.of("story.conception", "guided.premise", "guided.world", "guided.characters", "world.entry.generate", "world.*.generate")),
        Map.entry("role.outline", List.of("outline.chapter", "guided.outline", "guided.outline_develop", "guided.outline_rewrite", "guided.outline_expand")),
        Map.entry("role.setting_refine", List.of("story.refine", "character.refine", "outline.chapter_refine", "outline.scene_refine", "world.entry.refine", "world.*.refine", "generic.refine")),
        Map.entry("role.prose_draft", List.of("scene.fast.draft", "scene.crafted.draft")),
        Map.entry("role.prose_revise", List.of("scene.fast.length", "scene.crafted.length", "writing_agent.refine", "language.revise", "plot.revise", "assistant.auto.*", "assistant.edit.*")),
        Map.entry("role.setting_proposal", List.of("story_assistant.propose.*", "story_assistant.refine.*", "world_assistant.propose.*", "world_assistant.refine.*")),
        Map.entry("role.language_check", List.of("language.diagnose", "slop.judge", "slop.diagnose", "slop.drift")),
        Map.entry("role.plot_check", List.of("plot.diagnose")),
        Map.entry("role.revision_review", List.of("language.review")),
        Map.entry("role.agent_control", List.of("writing_agent.control")),
        Map.entry("role.discussion", List.of("generic.chat", "assistant.author_qa.*", "story_assistant.discuss.*", "world_assistant.discuss.*")),
        Map.entry("role.summarization", List.of("versions.summary", "world.overview.integrate")),
        Map.entry("role.fact_extraction", List.of("narrative.extract")),
        Map.entry("role.format_repair", List.of("guided.json_repair"))
    );

    private record Route(byte[] model, boolean billed, String defaultId) {}
    private record Option(String id, String key, byte[] model, boolean available, ObjectNode settings) {
        List<String> identity() {
            var result = new ArrayList<>(List.of(settings.path("modelKey").asText(), settings.path("thinkingMode").asText(),
                    settings.path("thinkingEffort").asText("")));
            if (settings.path("billingEnabled").asBoolean(true)) {
                result.addAll(List.of(settings.path("pricingMode").asText(), normalized(settings.path("fixedCredits")),
                        normalized(settings.path("inputPer1k")), normalized(settings.path("outputPer1k"))));
            }
            return result;
        }
        private static String normalized(JsonNode value) {
            return value.isNumber() ? value.decimalValue().stripTrailingZeros().toPlainString() : "";
        }
    }
    private record Preference(UUID user, String role) {}
    private final ObjectMapper json = new ObjectMapper();

    @Override
    public void migrate(Context context) throws Exception {
        var conn = context.getConnection();
        boolean ownsTransaction = conn.getAutoCommit();
        if (ownsTransaction) conn.setAutoCommit(false);
        try {
            consolidate(conn);
            if (ownsTransaction) conn.commit();
        } catch (Exception failure) {
            if (ownsTransaction) {
                try { conn.rollback(); }
                catch (SQLException rollbackFailure) { failure.addSuppressed(rollbackFailure); }
            }
            throw failure;
        } finally {
            if (ownsTransaction) conn.setAutoCommit(true);
        }
    }

    private void consolidate(Connection conn) throws Exception {
        var routes = readRoutes(conn);
        var options = readOptions(conn);
        var legacyRows = new HashSet<String>();
        for (String key : routes.keySet()) if (retired(key)) legacyRows.add(key);
        try (var statement = conn.prepareStatement("select distinct task_type from ai_model_options");
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                String key = rows.getString(1);
                if (retired(key)) legacyRows.add(key);
            }
        }

        for (String role : OLD_KEYS.keySet()) {
            if (!routes.containsKey(role)) consolidateRoute(conn, role, routes, options);
        }
        migratePreferences(conn, routes, options, legacyRows);
        for (String key : legacyRows) {
            execute(conn, "delete from user_model_preferences where task_type=?", key);
            execute(conn, "delete from ai_model_options where task_type=?", key);
            execute(conn, "delete from task_model_routing where task_type=?", key);
        }
        execute(conn, "update ai_model_catalog_state set revision=revision+1 where id=1");
    }

    private static String oldRole(String key) {
        for (var entry : OLD_KEYS.entrySet()) for (String pattern : entry.getValue()) {
            int wildcard = pattern.indexOf('*');
            boolean matches = wildcard < 0 ? key.equals(pattern) : key.startsWith(pattern.substring(0, wildcard))
                    && key.endsWith(pattern.substring(wildcard+1));
            if (matches) return entry.getKey();
        }
        return null;
    }

    private static boolean retired(String key) { return oldRole(key)!=null || key.equals("slop.revise"); }

    private static Map<String,Route> readRoutes(Connection conn) throws SQLException {
        var routes = new LinkedHashMap<String,Route>();
        try (var statement = conn.prepareStatement("""
                select task_type,recommended_model_id,billing_enabled,default_option_id from task_model_routing
                order by task_type
                """); var rows = statement.executeQuery()) {
            while (rows.next()) routes.put(rows.getString(1), new Route(rows.getBytes(2), rows.getBoolean(3), rows.getString(4)));
        }
        return routes;
    }

    private Map<String,Option> readOptions(Connection conn) throws Exception {
        var options = new LinkedHashMap<String,Option>();
        try (var statement = conn.prepareStatement("""
                select o.id,o.task_type,o.model_id,m.is_available,o.settings_json
                from ai_model_options o join model_registry m on m.id=o.model_id where o.active=true
                order by o.position_index,o.id
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                var option = new Option(rows.getString(1), rows.getString(2), rows.getBytes(3), rows.getBoolean(4),
                        (ObjectNode) json.readTree(rows.getString(5)));
                options.put(option.id(), option);
            }
        }
        return options;
    }

    private void consolidateRoute(Connection conn, String role, Map<String,Route> routes, Map<String,Option> options) throws Exception {
        var sources = routes.entrySet().stream().filter(entry -> role.equals(oldRole(entry.getKey()))).toList();
        if (sources.isEmpty()) return;
        var sample = sources.getFirst().getValue();
        var first = options.get(sample.defaultId());
        boolean compatible = first != null && sources.stream().allMatch(entry -> {
            var route = entry.getValue();
            var choice = options.get(route.defaultId());
            return choice != null && sample.billed()==route.billed() && first.identity().equals(choice.identity());
        });
        String defaultId = compatible ? copiedId(role, first.id()) : null;
        var route = new Route(sample.model(), sample.billed(), defaultId);
        execute(conn, """
                insert into task_model_routing(id,task_type,recommended_model_id,routing_strategy,config_json,created_at,updated_at,
                    billing_enabled,configuration_revision,default_option_id)
                values(?,?,?,'fixed','{}',current_timestamp(6),current_timestamp(6),?,0,?)
                """, bytes(UUID.randomUUID()), role, route.model(), route.billed(), defaultId);
        routes.put(role, route);
        if (!compatible) return;

        var distinct = new LinkedHashMap<List<String>,Option>();
        distinct.put(first.identity(), first);
        if (sample.billed()) {
            for (var option : options.values()) {
                var from = routes.get(option.key());
                if (from != null && from.billed() && role.equals(oldRole(option.key()))) {
                    distinct.putIfAbsent(option.identity(), option);
                }
            }
        }
        var labels = new HashSet<String>();
        int position = 0;
        for (var option : distinct.values()) {
            String id = copiedId(role, option.id());
            var settings = option.settings().deepCopy();
            String label = settings.path("label").asText();
            for (int suffix=position+1; !labels.add(label); suffix++) {
                String original = settings.path("label").asText();
                label = original.substring(0, Math.min(180, original.length())) + " · " + suffix;
            }
            settings.put("featureKey", role).put("optionId", id).put("label", label).put("revision", 0);
            execute(conn, "insert into ai_model_options(id,task_type,model_id,settings_json,active,position_index) values(?,?,?,?,true,?)",
                    id, role, option.model(), json.writeValueAsString(settings), position++);
            options.put(id, new Option(id, role, option.model(), option.available(), settings));
        }
    }

    private void migratePreferences(Connection conn, Map<String,Route> routes, Map<String,Option> options,
                                    Set<String> legacyRows) throws SQLException {
        var choices = new LinkedHashMap<Preference,Set<String>>();
        try (var statement = conn.prepareStatement("""
                select user_id,task_type,preferred_option_id,preferred_model_id from user_model_preferences
                """); var rows = statement.executeQuery()) {
            while (rows.next()) {
                String key = rows.getString(2);
                if (retired(key)) legacyRows.add(key);
                String role = oldRole(key);
                if (role == null || !routes.containsKey(role) || !routes.get(role).billed()) continue;
                String id = rows.getString(3);
                byte[] model = rows.getBytes(4);
                if (id == null && model == null) continue;
                var old = options.get(id);
                var candidates = options.values().stream().filter(option -> option.key().equals(role) && option.available())
                        .filter(option -> id != null ? old != null && old.key().equals(key) && old.identity().equals(option.identity())
                                : Arrays.equals(model, option.model())).toList();
                UUID user = uuid(rows.getBytes(1));
                choices.computeIfAbsent(new Preference(user, role), ignored -> new HashSet<>())
                        .add(candidates.size()==1 ? candidates.getFirst().id() : "");
            }
        }
        for (var entry : choices.entrySet()) {
            if (entry.getValue().size()!=1 || entry.getValue().contains("")) continue;
            execute(conn, """
                    insert into user_model_preferences(id,user_id,task_type,preferred_option_id,created_at,updated_at)
                    values(?,?,?,?,current_timestamp(6),current_timestamp(6)) on duplicate key update id=id
                    """, bytes(UUID.randomUUID()), bytes(entry.getKey().user()), entry.getKey().role(), entry.getValue().iterator().next());
        }
    }

    private static void execute(Connection conn, String sql, Object... args) throws SQLException {
        try (var statement = conn.prepareStatement(sql)) {
            for (int i=0; i<args.length; i++) statement.setObject(i+1, args[i]);
            statement.executeUpdate();
        }
    }

    private static UUID uuid(byte[] value) {
        var buffer = ByteBuffer.wrap(value);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
    private static String copiedId(String role, String id) {
        return UUID.nameUUIDFromBytes((role + "\n" + id).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
