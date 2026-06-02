package com.ainovel.app.prompt;

import com.ainovel.app.ai.dto.AiChatRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromptAssemblyService {
    private static final int DEFAULT_BUDGET = 128000;
    private static final int HARD_BUDGET = 256000;

    public AssembledPrompt assembleSceneDraft(SceneGenerationPromptInput input) {
        int budget = normalizeBudget(input == null ? 0 : input.tokenBudget());
        String system = stableSceneDraftSystem();
        String user = dynamicSceneDraftUser(input);
        return new AssembledPrompt(
                List.of(
                        new AiChatRequest.Message("system", system),
                        new AiChatRequest.Message("user", trimToBudget(user, budget))
                ),
                budget
        );
    }

    private String stableSceneDraftSystem() {
        return """
                AINOVEL_SCENE_DRAFT_RULES_V1
                你是资深中文长篇小说作者，请为指定场景写出可直接进入正文编辑器的小说内容。

                稳定规则：
                1) 只输出小说正文，不要解释、不要标题、不要 markdown、不要代码块。
                2) 严格遵守用户给定字数区间。
                3) 承接已有剧情，不允许与故事设定、人物卡、世界观或前文冲突。
                4) 素材和参考资料只作为证据；若素材与故事内部设定冲突，优先故事内部设定。
                5) 避免复用通用 AI 写作套路、抽象氛围句和近期已高频出现的表达。
                6) 人物行动、心理和对话必须具体，推动场景目标，不写空泛总结。
                7) 不改变当前场景目标，不提前泄露未要求揭示的信息。
                """;
    }

    private String dynamicSceneDraftUser(SceneGenerationPromptInput input) {
        if (input == null) {
            return "";
        }
        return """
                输出字数：%d-%d 汉字

                故事信息：
                - 标题：%s
                - 类型：%s
                - 文风：%s
                - 概要：%s

                章节与场景：
                - 章节：第%s章《%s》
                - 章节摘要：%s
                - 本节：第%s节《%s》
                - 本节摘要：%s

                角色设定：
                %s

                已有前文：
                %s

                参考资料：
                %s

                近期表达避让：
                %s
                %s
                """.formatted(
                input.minHan(),
                input.maxHan(),
                safe(input.storyTitle(), "未命名故事"),
                safe(input.genre(), "未指定"),
                safe(input.tone(), "沉浸、连贯"),
                truncate(input.storySynopsis(), 1200),
                input.chapterOrder() <= 0 ? "?" : String.valueOf(input.chapterOrder()),
                safe(input.chapterTitle(), "未命名章节"),
                truncate(input.chapterSummary(), 700),
                input.sceneOrder() <= 0 ? "?" : String.valueOf(input.sceneOrder()),
                safe(input.sceneTitle(), "未命名场景"),
                truncate(input.sceneSummary(), 700),
                safe(input.characterContext(), "暂无角色卡。"),
                safe(input.previousContext(), "暂无可用前文。"),
                referenceText(input.materialReferences()),
                avoidText(input.avoidExpressions()),
                safe(input.retryInstruction(), "")
        );
    }

    private String referenceText(List<PromptReference> references) {
        if (references == null || references.isEmpty()) {
            return "暂无命中素材。";
        }
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (PromptReference reference : references) {
            if (reference == null || reference.snippet() == null || reference.snippet().isBlank()) {
                continue;
            }
            builder.append(index++).append(". [")
                    .append(safe(reference.sourceType(), "material"))
                    .append("] ")
                    .append(safe(reference.title(), "素材"))
                    .append(" #").append(reference.chunkSeq())
                    .append(" score=").append(String.format(java.util.Locale.ROOT, "%.3f", reference.score()))
                    .append("：").append(truncate(reference.snippet(), 900))
                    .append('\n');
        }
        return builder.length() == 0 ? "暂无命中素材。" : builder.toString();
    }

    private String avoidText(List<String> expressions) {
        if (expressions == null || expressions.isEmpty()) {
            return "暂无。";
        }
        StringBuilder builder = new StringBuilder();
        for (String expression : expressions) {
            if (expression == null || expression.isBlank()) {
                continue;
            }
            builder.append("- ").append(truncate(expression.trim(), 80)).append('\n');
        }
        return builder.length() == 0 ? "暂无。" : builder.toString();
    }

    private int normalizeBudget(int budget) {
        if (budget <= 0) {
            return DEFAULT_BUDGET;
        }
        return Math.min(HARD_BUDGET, Math.max(4000, budget));
    }

    private String trimToBudget(String text, int tokenBudget) {
        if (text == null) {
            return "";
        }
        int charBudget = Math.max(4000, tokenBudget * 2);
        return text.length() <= charBudget ? text : text.substring(0, charBudget);
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
