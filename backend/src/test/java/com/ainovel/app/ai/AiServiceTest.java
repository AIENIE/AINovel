package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.modelconfig.AiModelConfigurationService;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiServiceTest {
    private final AiGatewayGrpcClient gateway = mock(AiGatewayGrpcClient.class);
    private final EconomyService economy = mock(EconomyService.class);
    private final AiAdmissionGuard guard = mock(AiAdmissionGuard.class);
    private final AiModelConfigurationService configuration = mock(AiModelConfigurationService.class);
    private final AiService service = new AiService(gateway, economy, guard, 2);
    private final User user = new User();

    AiServiceTest() {
        user.setRemoteUid(9000003L);
        ReflectionTestUtils.setField(service, "modelPolicy", new AiModelPolicy());
        ReflectionTestUtils.setField(service, "modelConfiguration", configuration);
        when(guard.requestHash(any())).thenReturn("hash");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "obsolete-client-model"})
    void legacyUnselectedRequestsStillUseAdmissionReservationAndConfiguredModel(String oldModel) {
        var request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "测试")), oldModel, null);
        when(economy.reserveAiUsage(user, 2, "AI_USAGE", "stable", "stable", "hash"))
                .thenReturn(EconomyService.AiReservationStart.reserved());
        when(gateway.chatCompletions(eq("stable"), eq(9000003L), eq("deepseek-v4-flash"), anyList()))
                .thenReturn(new AiGatewayGrpcClient.ChatResult("result", "deepseek-v4-flash", 100, 20, 60));
        when(economy.settleAiUsage(user, "stable", "result", 100, 20, 60))
                .thenReturn(new EconomyService.AiChargeResult(1, 99));
        when(economy.currentBalance(user)).thenReturn(new EconomyService.BalanceSnapshot(99, 0, 99));
        var result = service.chatWithIdempotency(user, request, "stable");
        assertEquals(60, result.usage().cacheTokens());
        assertEquals(.6, result.usage().cacheHitRate());
        assertEquals(1, result.usage().cost());
        var order = inOrder(guard, economy, gateway);
        order.verify(guard).acquire(user, request);
        order.verify(economy).reserveAiUsage(user, 2, "AI_USAGE", "stable", "stable", "hash");
        order.verify(gateway).chatCompletions(eq("stable"), eq(9000003L), eq("deepseek-v4-flash"), anyList());
        order.verify(economy).recordAiResult(user, "stable", "result", 100, 20, 60, "deepseek-v4-flash", result.metadata());
        order.verify(economy).settleAiUsage(user, "stable", "result", 100, 20, 60);
    }

    @Test
    void catalogComesFromCurrentConfiguration() {
        var catalog = List.of(new com.ainovel.app.ai.dto.AiModelDto("catalog-id", "catalog-model", "目录模型", "text", 1, 1, "provider", true, false, true));
        when(configuration.publicModels()).thenReturn(catalog);
        assertEquals(catalog, service.listModels(user));
        verify(configuration).publicModels();
    }

    @Test
    void unboundUsersCannotReachAdmissionOrGateway() {
        user.setRemoteUid(null);
        assertThrows(RuntimeException.class, () -> service.chatWithIdempotency(user,
                new AiChatRequest(List.of(new AiChatRequest.Message("user", "测试")), null, null), "stable"));
        verifyNoInteractions(guard, gateway, economy);
    }
}
