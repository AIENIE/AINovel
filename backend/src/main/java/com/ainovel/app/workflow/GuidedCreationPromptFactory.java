package com.ainovel.app.workflow;

import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import com.ainovel.app.workflow.model.GuidedCreationOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GuidedCreationPromptFactory {
    public String version(GuidedCreationStep step) {
        return version(step, GuidedCreationOperation.STEP_CANDIDATES);
    }

    public String version(GuidedCreationStep step, GuidedCreationOperation operation) {
        if (step != GuidedCreationStep.OUTLINE) {
            return "g1.quick-book." + step.name().toLowerCase() + ".v1";
        }
        return switch (operation) {
            case STEP_CANDIDATES -> "g1.quick-book.outline-directions.v2";
            case OUTLINE_DEVELOP -> "g1.quick-book.outline-development.v1";
            case OUTLINE_REWRITE -> "g1.quick-book.outline-rewrite.v1";
            case OUTLINE_EXPAND -> "g1.quick-book.outline-expansion.v1";
        };
    }

    public String build(CreationWorkflowRun run,
                        GuidedCreationStep step,
                        Map<String, Object> confirmedContext,
                        String hint) {
        String common = """
                你是中文小说策划编辑。请严格输出一个 JSON 对象，不要使用 Markdown 代码块。
                必须输出恰好 3 个有明显差异、可直接编辑的候选，并提供 recommendedIndex（0、1 或 2）。
                避免空洞形容、套路占位和三套仅换名称的结果。所有字段使用中文。
                用户的一句话想法：%s
                用户偏好体裁：%s
                用户偏好基调：%s
                已确认上下文：%s
                本次微调要求：%s
                """.formatted(
                run.getSeedIdea(), safe(run.getGenre()), safe(run.getTone()), confirmedContext, safe(hint));

        return common + switch (step) {
            case PREMISE -> """
                    输出结构：{"recommendedIndex":0,"candidates":[{"title":"","synopsis":"","genre":"","tone":"","corePromise":"","centralQuestion":"","hiddenTruth":""}]}。
                    每个 synopsis 150-300 字，方向之间应在冲突结构、主角欲望和代价上不同。
                    """;
            case WORLD -> """
                    输出结构：{"recommendedIndex":0,"candidates":[{"name":"","tagline":"","themes":[""],"creativeIntent":"","notes":"","modules":{"geography":{"terrain":"","climate":"","locations":""},"society":{"politics":"","economy":"","culture":""},"magic_tech":{"system_name":"","rules":"","limitations":""}}}]}。
                    三套世界观应服务同一故事核心，但在社会结构、力量规则和冲突来源上不同。
                    """;
            case CHARACTERS -> """
                    输出结构：{"recommendedIndex":0,"candidates":[{"label":"","characters":[{"name":"","synopsis":"","details":"","relationships":""}]}]}。
                    每套阵容必须包含 3-5 名主要角色，角色之间有具体欲望冲突和关系张力。
                    """;
            case OUTLINE -> """
                    输出结构：{"recommendedIndex":0,"candidates":[{"title":"","summary":"","coreConflict":"","protagonistDrive":"","stakes":"","routeHighlights":[""]}]}。
                    每个方向保持简洁，不得输出 chapters 或 scenes。三条方向必须在核心冲突、主角选择、代价和关键转折路线上明显不同。
                    """;
            case COMPLETED -> throw new IllegalArgumentException("完成状态不能生成候选");
        };
    }

    public String buildOutlineOperation(CreationWorkflowRun run,
                                        GuidedCreationOperation operation,
                                        Map<String, Object> confirmedContext,
                                        Map<String, Object> direction,
                                        Map<String, Object> payload) {
        String common = """
                你是中文小说策划编辑。请严格输出一个 JSON 对象，不要使用 Markdown 代码块。
                用户的一句话想法：%s
                用户偏好体裁：%s
                用户偏好基调：%s
                已确认上下文：%s
                当前根方向：%s
                用户编辑内容：%s
                当前发展稿：%s
                用户补充或反馈：%s
                """.formatted(
                run.getSeedIdea(), safe(run.getGenre()), safe(run.getTone()), confirmedContext,
                direction, payload.getOrDefault("editedPayload", Map.of()),
                direction.getOrDefault("development", Map.of()),
                safe(String.valueOf(payload.getOrDefault("instruction", ""))));
        return common + switch (operation) {
            case OUTLINE_DEVELOP -> """
                    请沿当前方向继续向下发展，补足因果链、人物选择、关键转折、升级节奏和结局落点。
                    输出结构：{"title":"","narrativeArc":"","characterChoices":[""],"keyTurns":[""],"escalation":"","endingDirection":""}。
                    只输出一份替换当前最新发展稿的完整 JSON，不要生成章节或场景。
                    """;
            case OUTLINE_REWRITE -> """
                    请严格根据用户反馈重新设计该方向的发展稿，不要沿用反馈明确否定的内容。
                    输出结构：{"title":"","narrativeArc":"","characterChoices":[""],"keyTurns":[""],"escalation":"","endingDirection":""}。
                    只输出一份替换当前最新发展稿的完整 JSON，不要生成章节或场景。
                    """;
            case OUTLINE_EXPAND -> """
                    将当前根方向和最新发展稿展开为一套完整、可直接编辑的章节/场景大纲。
                    输出结构：{"title":"","planning":{"corePromise":"","centralQuestion":"","stakes":""},"chapters":[{"title":"","summary":"","planning":{"purpose":"","tensionShift":""},"scenes":[{"title":"","summary":"","planning":{"goal":"","conflict":"","infoRelease":""}}]}]}。
                    必须包含 %d 章，每章 2-4 个场景。只输出这一套完整大纲，不要输出候选数组。
                    """.formatted(run.getTargetChapterCount());
            case STEP_CANDIDATES -> throw new IllegalArgumentException("初始候选应使用步骤提示词");
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
