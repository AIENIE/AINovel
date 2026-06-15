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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiServiceTest {

    @Test
    void shouldForceDeepseekV4FlashWhenRequestAsksForAnotherModel() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "deepseek-v4-flash", 5, 6, 0));
        when(economyService.chargeAiUsage(eq(user), eq(5L), eq(6L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99));

        aiService.chat(
                user,
                new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "gpt-4o", null)
        );

        verify(aiGatewayGrpcClient).chatCompletions(eq(9000003L), eq("deepseek-v4-flash"), anyList());
    }

    @Test
    void shouldExposeOnlyDeepseekV4FlashModel() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        List<AiModelDto> models = aiService.listModels(user);

        assertEquals(1, models.size());
        assertEquals("deepseek-v4-flash", models.get(0).id());
        assertEquals("deepseek-v4-flash", models.get(0).name());
        assertEquals("DeepSeek V4 Flash", models.get(0).displayName());
    }

    @Test
    void shouldForceDeepseekV4FlashWhenModelIdIsBlank() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "deepseek-v4-flash", 5, 6, 0));
        when(economyService.chargeAiUsage(eq(user), eq(5L), eq(6L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99));

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "", null);
        AiChatResponse response = aiService.chat(user, request);

        verify(aiGatewayGrpcClient).chatCompletions(eq(9000003L), eq("deepseek-v4-flash"), anyList());
        assertEquals(1.0, response.usage().cost());
        assertEquals(99.0, response.remainingCredits());
    }

    @Test
    void shouldIgnoreLegacyModelListWhenModelIdIsBlank() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "deepseek-v4-flash", 5, 6, 0));
        when(economyService.chargeAiUsage(eq(user), eq(5L), eq(6L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99));

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "", null);
        aiService.chat(user, request);

        verify(aiGatewayGrpcClient).chatCompletions(eq(9000003L), eq("deepseek-v4-flash"), anyList());
    }

    @Test
    void shouldRejectUsersWithoutRemoteUid() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();
        user.setRemoteUid(null);

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "2", null);

        assertThrows(RuntimeException.class, () -> aiService.chat(user, request));
    }

    @Test
    void shouldRejectRequestsWithoutNonEmptyUserMessage() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("assistant", "你好")), "2", null);

        assertThrows(RuntimeException.class, () -> aiService.chat(user, request));
    }

    @Test
    void shouldExposeCacheTokensFromGatewayUsage() {
        AiGatewayGrpcClient aiGatewayGrpcClient = mock(AiGatewayGrpcClient.class);
        EconomyService economyService = mock(EconomyService.class);
        AiService aiService = new AiService(aiGatewayGrpcClient, economyService);
        User user = user();

        when(aiGatewayGrpcClient.chatCompletions(anyLong(), anyString(), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("ok", "gpt-cache", 100, 20, 60));
        when(economyService.chargeAiUsage(eq(user), eq(100L), eq(20L), anyString()))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economyService.currentBalance(eq(user)))
                .thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99));

        AiChatResponse response = aiService.chat(
                user,
                new AiChatRequest(List.of(new AiChatRequest.Message("user", "你好")), "gpt-cache", null)
        );

        assertEquals(60.0, response.usage().cacheTokens());
        assertEquals(0.6d, response.usage().cacheHitRate());
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
