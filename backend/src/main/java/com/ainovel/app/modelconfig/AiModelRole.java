package com.ainovel.app.modelconfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Model configuration identities shared by every caller. */
public enum AiModelRole {
    CONCEPTION("conception", "creation", "构思与设定补全", "構思與設定補全", "Concept and setting generation"),
    OUTLINE("outline", "creation", "大纲规划", "大綱規劃", "Outline planning"),
    SETTING_REFINE("setting_refine", "creation", "设定与摘要润色", "設定與摘要潤色", "Refine settings and summaries"),
    PROSE_DRAFT("prose_draft", "creation", "正文起草", "正文起草", "Prose drafting"),
    PROSE_REVISE("prose_revise", "creation", "正文修订", "正文修訂", "Prose revision"),
    SETTING_PROPOSAL("setting_proposal", "creation", "设定提案", "設定提案", "Setting proposals"),
    LANGUAGE_CHECK("language_check", "review", "语言与文风检查", "語言與文風檢查", "Language and style diagnosis"),
    PLOT_CHECK("plot_check", "review", "剧情诊断", "劇情診斷", "Plot diagnosis"),
    REVISION_REVIEW("revision_review", "review", "修订复核", "修訂複核", "Revision review"),
    AGENT_CONTROL("agent_control", "support", "自主创作协调", "自主創作協調", "Writing agent coordination"),
    DISCUSSION("discussion", "support", "讨论问答", "討論問答", "Discussion and Q&A"),
    SUMMARIZATION("summarization", "support", "资料归纳", "資料歸納", "Summarization"),
    FACT_EXTRACTION("fact_extraction", "support", "叙事事实提取", "敘事事實提取", "Narrative fact extraction"),
    FORMAT_REPAIR("format_repair", "support", "格式修复", "格式修復", "Format repair");

    public record Definition(String key, String group, String zh, String tw, String en) {}

    private static final Map<String, AiModelRole> BY_KEY = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(AiModelRole::key, Function.identity()));
    private final Definition definition;

    AiModelRole(String key, String group, String zh, String tw, String en) {
        definition = new Definition("role." + key, group, zh, tw, en);
    }

    public Definition definition() { return definition; }
    @JsonValue public String key() { return definition.key(); }
    public static AiModelRole find(String key) { return key == null ? null : BY_KEY.get(key); }

    @JsonCreator
    public static AiModelRole require(String key) {
        AiModelRole role = find(key);
        if (role == null) throw new IllegalArgumentException("Unknown model responsibility: " + key);
        return role;
    }
}
