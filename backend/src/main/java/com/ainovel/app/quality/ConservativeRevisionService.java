package com.ainovel.app.quality;

import com.ainovel.app.user.User;

@FunctionalInterface
public interface ConservativeRevisionService {
    String revise(User user, SlopQualityRequest request, SlopJudgeResult judgeResult);
}
