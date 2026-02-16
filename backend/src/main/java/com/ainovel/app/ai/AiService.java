package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiService {

    private final AiGatewayGrpcClient aiGatewayGrpcClient;
    private final EconomyService economyService;

    public AiService(AiGatewayGrpcClient aiGatewayGrpcClient, EconomyService economyService) {
        this.aiGatewayGrpcClient = aiGatewayGrpcClient;
        this.economyService = economyService;
    }

    public List<AiModelDto> listModels() {
        return aiGatewayGrpcClient.listModels();
    }

    public AiChatResponse chat(User user, AiChatRequest request) {
        Long remoteUid = user.getRemoteUid();
        if (remoteUid == null || remoteUid <= 0) {
            throw new RuntimeException("当前账号未绑定统一用户，无法调用 AI 服务");
        }
        String model = request.modelId();
        if (model == null || model.isBlank()) {
            List<AiModelDto> models = listModels();
            if (!models.isEmpty()) {
                model = models.get(0).id();
            }
        }
        AiGatewayGrpcClient.ChatResult result = aiGatewayGrpcClient.chatCompletions(remoteUid, model, request.messages());
        double remaining = economyService.currentBalance(user);
        return new AiChatResponse(
                "assistant",
                result.content(),
                new AiUsageDto((int) result.promptTokens(), (int) result.completionTokens(), 0.0),
                remaining
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
}
