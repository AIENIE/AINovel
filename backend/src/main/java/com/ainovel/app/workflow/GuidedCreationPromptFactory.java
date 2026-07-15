package com.ainovel.app.workflow;

import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.GuidedCreationStep;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GuidedCreationPromptFactory {
    public String version(GuidedCreationStep step) {
        return "g1.quick-book." + step.name().toLowerCase() + ".v1";
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
                    输出结构：{"recommendedIndex":0,"candidates":[{"title":"","planning":{"corePromise":"","centralQuestion":"","stakes":""},"chapters":[{"title":"","summary":"","planning":{"purpose":"","tensionShift":""},"scenes":[{"title":"","summary":"","planning":{"goal":"","conflict":"","infoRelease":""}}]}]}]}。
                    每套大纲必须包含 %d 章，每章 2-4 个场景，推进同一核心承诺但采用不同节奏和转折路线。
                    """.formatted(run.getTargetChapterCount());
            case COMPLETED -> throw new IllegalArgumentException("完成状态不能生成候选");
        };
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
