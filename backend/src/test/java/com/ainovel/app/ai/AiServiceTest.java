package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.ai.dto.AiChatResponse;
import com.ainovel.app.ai.dto.AiModelDto;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceTest {

    @Test
    void shouldPreferTextModelWhenModelIdIsBlank() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.listModels()).thenReturn(List.of(
                new AiModelDto("1", "1", "text-embedding-3-small", "embedding", 1, 1, "openai", true),
                new AiModelDto("2", "2", "gemini-2.5-flash", "text", 1, 1, "google", true),
                new AiModelDto("3", "3", "GLM OCR", "unspecified", 1, 1, "zhipu", true)
        ));
        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "2", 5, 6));
        when(economyService.chargeAiUsage(eq(user), eq(5L), eq(6L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99, null));

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "", null);
        AiChatResponse response = aiService.chat(user, request);

        verify(aiGatewayGrpcClient).chatCompletions(eq(9000003L), eq("2"), anyList());
        assertEquals(1.0, response.usage().cost());
        assertEquals(99.0, response.remainingCredits());
    }

    @Test
    void shouldSkipEmbeddingAndOcrWhenNoTypedTextModel() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.listModels()).thenReturn(List.of(
                new AiModelDto("1", "1", "GLM OCR", "unspecified", 1, 1, "zhipu", true),
                new AiModelDto("3", "3", "text-embedding-3-small", "unspecified", 1, 1, "openai", true),
                new AiModelDto("2", "2", "gemini-2.5-flash", "unspecified", 1, 1, "google", true)
        ));
        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "2", 5, 6));
        when(economyService.chargeAiUsage(eq(user), eq(5L), eq(6L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99, null));

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "", null);
        aiService.chat(user, request);

        verify(aiGatewayGrpcClient).chatCompletions(eq(9000003L), eq("2"), anyList());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.fromString("0f41d89f-e04f-47e2-aa87-c2bf9a29fd0f"));
        user.setUsername("flowuser");
        user.setEmail("flowuser@example.com");
        user.setPasswordHash("hashed");
        user.setRemoteUid(9000003L);
        return user;
    }
}
