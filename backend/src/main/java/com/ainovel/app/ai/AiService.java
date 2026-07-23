package com.ainovel.app.ai;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

@Service
public class AiService {

    private final AiGatewayGrpcClient aiGatewayGrpcClient;
    private final EconomyService economyService;
    private volatile Instant streamingCapabilityCheckedAt = Instant.EPOCH;
    private volatile boolean streamingCapabilityAvailable;

    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
    }

    public List<AiModelDto> listModels(User user) {
        resolveGatewayUserId(user);
        return List.of(AiModelPolicy.requiredTextModel());
    }

    public AiChatResponse chat(User user, AiChatRequest request) {
        return chat(user, request, null);
    }

    public AiChatResponse chat(User user, AiChatRequest request, AiUsageContext usageContext) {
        Long remoteUid = resolveGatewayUserId(user);
        validateChatMessages(request.messages());
        var progressListener = AiProgressContext.current();
        if (progressListener != null && !supportsRequiredModelStreaming(remoteUid)) {
            throw new BusinessException("当前 AI 模型不支持真实流式输出，请检查 ai-service 模型配置");
        }
        AiGatewayGrpcClient.ChatResult result = progressListener == null
                ? aiGatewayGrpcClient.chatCompletions(remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, request.messages())
                : aiGatewayGrpcClient.chatCompletionsStream(
                        remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, request.messages(), progressListener);
        EconomyService.AiChargeResult charge = usageContext == null
                ? economyService.chargeAiUsage(
                        user,
                        result.promptTokens(),
                        result.completionTokens(),
                        UUID.randomUUID().toString()
                )
                : economyService.chargeAiUsage(
                        user,
                        result.promptTokens(),
                        result.completionTokens(),
                        usageContext.referenceType(),
                        usageContext.referenceId(),
                        usageContext.idempotencyKey()
                );
        var balance = economyService.currentBalance(user);
        return new AiChatResponse(
                "assistant",
                result.content(),
                new AiUsageDto(
                        (int) result.promptTokens(),
                        (int) result.completionTokens(),
                        (int) result.cacheTokens(),
                        cacheHitRate(result.promptTokens(), result.cacheTokens()),
                        charge.charged()
                ),
                balance.totalCredits()
        );
    }

    public AiRefineResponse refine(User user, AiRefineRequest request) {
        String instruction = request.instruction() == null ? "" : request.instruction();
        AiChatRequest chatRequest = new AiChatRequest(
                List.of(new AiChatRequest.Message("user", "请根据以下指令润色文本。\n\n指令:\n" + instruction + "\n\n文本:\n" + request.text())),
                request.modelId(),
                null
        );
        AiChatResponse resp = chat(user, chatRequest);
        return new AiRefineResponse(resp.content(), resp.usage(), resp.remainingCredits());
    }

    private Long resolveGatewayUserId(User user) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid != null && remoteUid > 0) {
            return remoteUid;
        }
        throw new BusinessException("当前账号未绑定统一用户，无法调用 AI 服务");
    }

    private void validateChatMessages(List<AiChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new BusinessException("messages 至少需要一条非空 user 消息");
        }
        boolean hasUserMessage = false;
        for (AiChatRequest.Message message : messages) {
            if (message == null) {
                continue;
            }
            String role = message.role() == null ? "" : message.role().trim().toLowerCase(Locale.ROOT);
            String content = message.content() == null ? "" : message.content().trim();
            if ("user".equals(role) && !content.isBlank()) {
                hasUserMessage = true;
                break;
            }
        }
        if (!hasUserMessage) {
            throw new BusinessException("messages 至少需要一条非空 user 消息");
        }
    }

    private double cacheHitRate(long promptTokens, long cacheTokens) {
        if (promptTokens <= 0 || cacheTokens <= 0) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, cacheTokens / (double) promptTokens));
    }

    private synchronized boolean supportsRequiredModelStreaming(long remoteUid) {
        Instant now = Instant.now();
        if (streamingCapabilityCheckedAt.plusSeconds(60).isAfter(now)) {
            return streamingCapabilityAvailable;
        }
        streamingCapabilityAvailable = aiGatewayGrpcClient.listModels(remoteUid).stream()
                .anyMatch(model -> model.supportsStreaming()
                        && (AiModelPolicy.REQUIRED_TEXT_MODEL_KEY.equalsIgnoreCase(model.id())
                        || AiModelPolicy.REQUIRED_TEXT_MODEL_KEY.equalsIgnoreCase(model.name())
                        || AiModelPolicy.REQUIRED_TEXT_MODEL_DISPLAY_NAME.equalsIgnoreCase(model.displayName())));
        streamingCapabilityCheckedAt = now;
        return streamingCapabilityAvailable;
    }
}
