package com.ainovel.app.quality;

import com.ainovel.app.ai.AiUsageContext;
import com.ainovel.app.user.User;

@FunctionalInterface
public interface SlopJudgeClient {
    SlopJudgeResult judge(User user, SlopQualityRequest request, SlopHeuristicResult heuristicResult);

    default SlopJudgeResult judge(User user,
                                  SlopQualityRequest request,
                                  SlopHeuristicResult heuristicResult,
                                  AiUsageContext usageContext) {
        return judge(user, request, heuristicResult);
    }
}
