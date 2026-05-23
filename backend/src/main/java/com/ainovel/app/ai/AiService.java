package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiService {

    private final AiGatewayGrpcClient aiGatewayGrpcClient;
    private final EconomyService economyService;
    @Value("${app.ai.model:gpt-4o}")
    private String configuredDefaultModel;

    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
    }

    public List<AiModelDto> listModels(User user) {
        return aiGatewayGrpcClient.listModels(resolveGatewayUserId(user));
    }

    public AiChatResponse chat(User user, AiChatRequest request) {
        Long remoteUid = resolveGatewayUserId(user);
        validateChatMessages(request.messages());
        String model = normalizeModelKey(request.modelId());
        String fallbackModel = null;
        if (model == null || model.isBlank()) {
            model = normalizeModelKey(configuredDefaultModel);
        }
        if (model != null && !model.isBlank()) {
            fallbackModel = pickDefaultChatModel(aiGatewayGrpcClient.listModels(remoteUid));
        } else {
            model = pickDefaultChatModel(aiGatewayGrpcClient.listModels(remoteUid));
        }
        AiGatewayGrpcClient.ChatResult result;
        try {
            result = aiGatewayGrpcClient.chatCompletions(remoteUid, model, request.messages());
        } catch (RuntimeException ex) {
            if (fallbackModel == null || fallbackModel.isBlank() || fallbackModel.equals(model)) {
                throw ex;
            }
            result = aiGatewayGrpcClient.chatCompletions(remoteUid, fallbackModel, request.messages());
        }
        EconomyService.AiChargeResult charge = economyService.chargeAiUsage(
                user,
                result.promptTokens(),
                result.completionTokens(),
                UUID.randomUUID().toString()
        );
        var balance = economyService.currentBalance(user);
        return new AiChatResponse(
                "assistant",
                result.content(),
                new AiUsageDto((int) result.promptTokens(), (int) result.completionTokens(), charge.charged()),
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
        throw new RuntimeException("当前账号未绑定统一用户，无法调用 AI 服务");
    }

    private void validateChatMessages(List<AiChatRequest.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new RuntimeException("messages 至少需要一条非空 user 消息");
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
            throw new RuntimeException("messages 至少需要一条非空 user 消息");
        }
    }

    private String pickDefaultChatModel(List<AiModelDto> models) {
        if (models == null || models.isEmpty()) {
            return null;
        }
        for (AiModelDto model : models) {
            if (model == null || model.id() == null || model.id().isBlank()) {
                continue;
            }
            String type = model.modelType() == null ? "" : model.modelType().trim().toLowerCase(Locale.ROOT);
            if ("text".equals(type)) {
                String picked = normalizeModelKey(model.name());
                if (picked != null && !picked.isBlank()) {
                    return picked;
                }
                picked = normalizeModelKey(model.id());
                if (picked != null && !picked.isBlank()) {
                    return picked;
                }
            }
        }
        for (AiModelDto model : models) {
            if (model == null || model.id() == null || model.id().isBlank()) {
                continue;
            }
            String name = (model.displayName() == null ? "" : model.displayName())
                    + " "
                    + (model.name() == null ? "" : model.name());
            String normalized = name.toLowerCase(Locale.ROOT);
            if (normalized.contains("embedding") || normalized.contains("ocr")) {
                continue;
            }
            String picked = normalizeModelKey(model.name());
            if (picked != null && !picked.isBlank()) {
                return picked;
            }
            picked = normalizeModelKey(model.id());
            if (picked != null && !picked.isBlank()) {
                return picked;
            }
        }
        AiModelDto first = models.get(0);
        String picked = normalizeModelKey(first.name());
        if (picked != null && !picked.isBlank()) {
            return picked;
        }
        return normalizeModelKey(first.id());
    }

    private String normalizeModelKey(String raw) {
        if (raw == null) {
            return null;
        }
        String model = raw.trim();
        if (model.isBlank()) {
            return null;
        }
        if (looksLikeUuid(model)) {
            return null;
        }
        return model;
    }

    private boolean looksLikeUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
