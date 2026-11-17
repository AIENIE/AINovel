package com.ainovel.app.modelconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The operation remains the execution identity. Responsibilities own the shared model settings. */
public final class AiModelResponsibilities {
    public static final String AGENT_CONTROL = "role.agent_control";

    public record Role(AiFeatures.Definition definition, List<AiFeatures.Definition> operations) {
        public String key() { return definition.key(); }
    }

    private static final Map<String, Role> ROLES = new LinkedHashMap<>();
    private static final Map<String, Role> BY_OPERATION = new LinkedHashMap<>();

    static {
        add("conception", "creation", "构思与设定补全", "構思與設定補全", "Concept and setting generation",
                "story.conception", "guided.premise", "guided.world", "guided.characters", "world.entry.generate", "world.*.generate");
        add("outline", "creation", "大纲规划", "大綱規劃", "Outline planning",
                "outline.chapter", "guided.outline", "guided.outline_develop", "guided.outline_rewrite", "guided.outline_expand");
        add("setting_refine", "creation", "设定与摘要润色", "設定與摘要潤色", "Refine settings and summaries",
                "story.refine", "character.refine", "outline.chapter_refine", "outline.scene_refine", "world.entry.refine", "world.*.refine", "generic.refine");
        add("prose_draft", "creation", "正文起草", "正文起草", "Prose drafting", "scene.fast.draft", "scene.crafted.draft");
        add("prose_revise", "creation", "正文修订", "正文修訂", "Prose revision",
                "scene.fast.length", "scene.crafted.length", "writing_agent.refine", "language.revise", "plot.revise", "assistant.auto.*", "assistant.edit.*");
        add("setting_proposal", "creation", "设定提案", "設定提案", "Setting proposals",
                "story_assistant.propose.*", "story_assistant.refine.*", "world_assistant.propose.*", "world_assistant.refine.*");
        add("language_check", "review", "语言与文风检查", "語言與文風檢查", "Language and style diagnosis",
                "language.diagnose", "slop.judge", "slop.diagnose", "slop.drift");
        add("plot_check", "review", "剧情诊断", "劇情診斷", "Plot diagnosis", "plot.diagnose");
        add("revision_review", "review", "修订复核", "修訂複核", "Revision review", "language.review");
        add("agent_control", "support", "自主创作协调", "自主創作協調", "Writing agent coordination", "writing_agent.control");
        add("discussion", "support", "讨论问答", "討論問答", "Discussion and Q&A",
                "generic.chat", "assistant.author_qa.*", "story_assistant.discuss.*", "world_assistant.discuss.*");
        add("summarization", "support", "资料归纳", "資料歸納", "Summarization", "versions.summary", "world.overview.integrate");
        add("fact_extraction", "support", "叙事事实提取", "敘事事實提取", "Narrative fact extraction", "narrative.extract");
        add("format_repair", "support", "格式修复", "格式修復", "Format repair", "guided.json_repair");

        for (var operation : AiFeatures.all()) {
            if (!operation.retired() && !BY_OPERATION.containsKey(operation.key())) {
                throw new IllegalStateException("No model responsibility for " + operation.key());
            }
        }
    }

    private AiModelResponsibilities() {}

    private static void add(String key, String group, String zh, String tw, String en, String... patterns) {
        var operations = new ArrayList<AiFeatures.Definition>();
        for (String pattern : patterns) {
            int wildcard = pattern.indexOf('*');
            if (wildcard < 0) {
                operations.add(AiFeatures.require(pattern));
                continue;
            }
            var matches = AiFeatures.all().stream().filter(operation -> {
                String operationKey = operation.key();
                return operationKey.startsWith(pattern.substring(0, wildcard))
                        && operationKey.endsWith(pattern.substring(wildcard + 1))
                        && !operations.contains(operation);
            }).toList();
            if (matches.isEmpty()) throw new IllegalStateException("No operations match " + pattern);
            operations.addAll(matches);
        }
        var role = new Role(new AiFeatures.Definition("role." + key, group, zh, tw, en), List.copyOf(operations));
        ROLES.put(role.key(), role);
        for (var operation : operations) {
            if (operation.retired() || BY_OPERATION.putIfAbsent(operation.key(), role) != null) {
                throw new IllegalStateException("Invalid model responsibility for " + operation.key());
            }
        }
    }

    public static List<Role> all() { return List.copyOf(ROLES.values()); }
    public static Role forOperation(String key) { return BY_OPERATION.get(key); }
    public static Role byKey(String key) { return ROLES.get(key); }
}
