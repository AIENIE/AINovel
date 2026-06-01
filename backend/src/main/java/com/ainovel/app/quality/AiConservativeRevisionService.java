package com.ainovel.app.quality;

import com.ainovel.app.ai.AiService;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AiConservativeRevisionService implements ConservativeRevisionService {
    private final AiService aiService;

    public AiConservativeRevisionService(AiService aiService) {
        this.aiService = aiService;
    }

    @Override
    public String revise(User user, SlopQualityRequest request, SlopJudgeResult judgeResult) {
        String revised = aiService.chat(user, new AiChatRequest(
                List.of(new AiChatRequest.Message("user", buildPrompt(request, judgeResult))),
                null,
                null
        )).content();
        return stripWrapper(revised);
    }

    private String buildPrompt(SlopQualityRequest request, SlopJudgeResult judgeResult) {
        StringBuilder issueText = new StringBuilder();
        for (SlopIssueDraft issue : judgeResult.issues()) {
            issueText.append("- ")
                    .append(issue.dimension()).append(" / ").append(issue.severity())
                    .append("：").append(issue.evidence())
                    .append("；建议：").append(issue.minimalFix())
                    .append('\n');
        }
        return """
                你是中文小说保守修订器。请只对候选正文做最小必要修订。
                硬性约束：
                1) 不改变剧情事件、结局、角色决策、人物关系和新增设定。
                2) 不新增关键事实，不删除关键线索。
                3) 只处理重复、套话、AI 输出伪迹、局部承接和轻量风格漂移。
                4) 保持原文大致长度和文风，只输出修订后的小说正文。
                5) 不要解释、不要标题、不要 markdown、不要 JSON。

                故事：%s
                类型：%s
                文风目标：%s
                章节：%s
                场景：%s
                场景摘要：%s
                角色上下文：%s
                前文：%s

                问题证据：
                %s

                候选正文：
                %s
                """.formatted(
                safe(request.storyTitle()),
                safe(request.genre()),
                safe(request.tone()),
                safe(request.chapterTitle()),
                safe(request.sceneTitle()),
                safe(request.sceneSummary()),
                truncate(request.characterContext(), 1000),
                truncate(request.previousContext(), 1000),
                issueText,
                truncate(request.candidateText(), 6000)
        );
    }

    private String stripWrapper(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstLineEnd = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
                text = text.substring(firstLineEnd + 1, lastFence).trim();
            }
        }
        return text;
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "未指定" : value;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() <= limit ? value : value.substring(0, limit);
    }
}
