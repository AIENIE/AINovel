package com.ainovel.app.ai;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.time.Instant;

@Service
public class AiService {

    private final AiGatewayGrpcClient aiGatewayGrpcClient;
    private final EconomyService economyService;
    private final AiAdmissionGuard admissionGuard;
    private final long reservationCredits;
    private final boolean reservationEnabled;
    private volatile Instant streamingCapabilityCheckedAt = Instant.EPOCH;
    private volatile boolean streamingCapabilityAvailable;

    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
        this.admissionGuard = null;
        this.reservationCredits = 2L;
        this.reservationEnabled = false;
    }

    @Autowired
    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService,
                     AiAdmissionGuard admissionGuard,
                     @Value("${app.ai.admission.reservation-credits:2}") long reservationCredits) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
        this.admissionGuard = admissionGuard;
        this.reservationCredits = Math.max(1L, reservationCredits);
        this.reservationEnabled = true;
    }

    public List<AiModelDto> listModels(User user) {
        resolveGatewayUserId(user);
        return List.of(AiModelPolicy.requiredTextModel());
    }

    public AiChatResponse chat(User user, AiChatRequest request) {
        return chat(user, request, null);
    }

    public AiChatResponse chat(User user, AiChatRequest request, AiUsageContext usageContext) {
        String key = usageContext == null ? UUID.randomUUID().toString() : usageContext.idempotencyKey();
        return executeChat(user, request, usageContext, key);
    }

    public AiChatResponse chatWithIdempotency(User user, AiChatRequest request, String idempotencyKey) {
        return executeChat(user, request, null, normalizeIdempotencyKey(idempotencyKey));
    }

    private AiChatResponse executeChat(User user, AiChatRequest request, AiUsageContext usageContext,
                                       String idempotencyKey) {
        Long remoteUid = resolveGatewayUserId(user);
        if (admissionGuard == null) validateChatMessages(request.messages());
        final AiAdmissionGuard.Lease lease = admissionGuard == null ? null : admissionGuard.acquire(user, request);
        try (lease) {
            if (!reservationEnabled) {
                return executeLegacyChat(user, request, usageContext, remoteUid);
            }
            String referenceType = usageContext == null ? "AI_USAGE" : usageContext.referenceType();
            String referenceId = usageContext == null ? idempotencyKey : usageContext.referenceId();
            String requestHash = admissionGuard.requestHash(request);
            EconomyService.AiReservationStart reservation = economyService.reserveAiUsage(
                    user, reservationCredits, referenceType, referenceId, idempotencyKey, requestHash);
            if (reservation.replay()) {
                return response(user, reservation.content(), reservation.promptTokens(), reservation.completionTokens(),
                        reservation.cacheTokens(), reservation.charged());
            }
            try {
                AiGatewayGrpcClient.ChatResult result = invokeGateway(remoteUid, request, idempotencyKey);
                EconomyService.AiChargeResult charge = economyService.settleAiUsage(user, idempotencyKey,
                        result.content(), result.promptTokens(), result.completionTokens(), result.cacheTokens());
                return response(user, result.content(), result.promptTokens(), result.completionTokens(),
                        result.cacheTokens(), charge.charged());
            } catch (RuntimeException ex) {
                economyService.releaseAiReservation(user, idempotencyKey);
                throw ex;
            }
        }
    }

    private AiChatResponse executeLegacyChat(User user, AiChatRequest request, AiUsageContext usageContext, Long remoteUid) {
        AiGatewayGrpcClient.ChatResult result = invokeGateway(remoteUid, request, null);
        EconomyService.AiChargeResult charge = usageContext == null
                ? economyService.chargeAiUsage(user, result.promptTokens(), result.completionTokens(), UUID.randomUUID().toString())
                : economyService.chargeAiUsage(user, result.promptTokens(), result.completionTokens(),
                        usageContext.referenceType(), usageContext.referenceId(), usageContext.idempotencyKey());
        return response(user, result.content(), result.promptTokens(), result.completionTokens(), result.cacheTokens(), charge.charged());
    }

    private AiGatewayGrpcClient.ChatResult invokeGateway(Long remoteUid, AiChatRequest request, String requestId) {
        var progressListener = AiProgressContext.current();
        if (progressListener != null && !supportsRequiredModelStreaming(remoteUid)) {
            throw new BusinessException("当前 AI 模型不支持真实流式输出，请检查 ai-service 模型配置");
        }
        if (progressListener == null) {
            return requestId == null
                    ? aiGatewayGrpcClient.chatCompletions(remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, request.messages())
                    : aiGatewayGrpcClient.chatCompletions(requestId, remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, request.messages());
        }
        return requestId == null
                ? aiGatewayGrpcClient.chatCompletionsStream(remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY, request.messages(), progressListener)
                : aiGatewayGrpcClient.chatCompletionsStream(requestId, remoteUid, AiModelPolicy.REQUIRED_TEXT_MODEL_KEY,
                    request.messages(), progressListener);
    }

    private AiChatResponse response(User user, String content, long promptTokens, long completionTokens,
                                    long cacheTokens, long charged) {
        var balance = economyService.currentBalance(user);
        return new AiChatResponse("assistant", content,
                new AiUsageDto((int) promptTokens, (int) completionTokens, (int) cacheTokens,
                        cacheHitRate(promptTokens, cacheTokens), charged), balance.totalCredits());
    }

    public AiRefineResponse refine(User user, AiRefineRequest request) {
        return refine(user, request, null);
    }

    public AiRefineResponse refine(User user, AiRefineRequest request, String idempotencyKey) {
        String instruction = request.instruction() == null ? "" : request.instruction();
        AiChatRequest chatRequest = new AiChatRequest(
                List.of(new AiChatRequest.Message("user", "请根据以下指令润色文本。\n\n指令:\n" + instruction + "\n\n文本:\n" + request.text())),
                request.modelId(),
                null
        );
        AiChatResponse resp = idempotencyKey == null
                ? chat(user, chatRequest)
                : chatWithIdempotency(user, chatRequest, idempotencyKey);
        return new AiRefineResponse(resp.content(), resp.usage(), resp.remainingCredits());
    }

    private String normalizeIdempotencyKey(String raw) {
        String key = raw == null ? "" : raw.trim();
        if (key.isBlank() || key.length() > 128) {
            throw new BusinessException("Idempotency-Key 必须为 1-128 个字符");
        }
        return key;
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
