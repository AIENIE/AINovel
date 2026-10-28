package com.ainovel.app.writingagent;

import com.ainovel.app.ai.*;
import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.modelconfig.AiModelSelection;
import com.ainovel.app.modelconfig.AiSelectionContext;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.ainovel.app.writingagent.WritingAgentDtos.ModelRecord;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WritingAgentModelRecoveryTest {
    private final WritingAgentStore store = mock(WritingAgentStore.class);
    private final WritingAgentMoney money = mock(WritingAgentMoney.class);
    private final EconomyService economy = mock(EconomyService.class);
    private final AiService ai = mock(AiService.class);
    private final AiGatewayGrpcClient gateway = mock(AiGatewayGrpcClient.class);
    private final AiValidationMoneyGateway.Transport wire = mock(AiValidationMoneyGateway.Transport.class);
    private final WritingAgentService service = mock(WritingAgentService.class, CALLS_REAL_METHODS);
    private final UUID run = UUID.randomUUID();
    private final User user = new User();
    private final AiChatRequest request = new AiChatRequest(List.of(new AiChatRequest.Message("user", "冻结请求")), null, null).forFeature("writing_agent.control");
    private final AiModelSelection selected = new AiModelSelection("writing_agent.control", "option", "model", "Model",
            "OFF", null, false, true, "FIXED", 1L, null, null, 1);

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "store", store);
        ReflectionTestUtils.setField(service, "money", money);
        ReflectionTestUtils.setField(service, "economy", economy);
        ReflectionTestUtils.setField(service, "ai", ai);
        ReflectionTestUtils.setField(service, "gateway", gateway);
        when(gateway.moneyTransport()).thenReturn(wire);
        var step = new WritingAgentStore.Step(UUID.randomUUID(), run, 3, "control:2", "MODEL", request.featureKey(),
                "hash", "frozen-request", null, "original-gateway-request", "original-budget", null,
                "RECONCILIATION_REQUIRED", BigDecimal.ONE, null, 2L, null);
        when(store.step(run, "control:2")).thenReturn(step);
        when(store.read("frozen-request", ModelRecord.class)).thenReturn(new ModelRecord(request, request.featureKey()));
    }

    @Test
    void authoritativeNotSentRecoveryReleasesTheOriginalAgentHoldAndNeverCallsTheModel() {
        var notSent = new AiRequestNotSentException(HttpStatus.PRECONDITION_FAILED, "WRITING_AGENT_CONFIRMED_NOT_SENT");
        when(money.recoverIfPresent(run, "control:2", wire)).thenThrow(notSent);
        try (var ignored = AiSelectionContext.install(new AiSelectionContext.Snapshot("agent-run", Map.of(selected.featureKey(), selected)))) {
            assertSame(notSent, assertThrows(AiRequestNotSentException.class, () -> service.model(user, run, 3, "control:2", request)));
        }
        verify(economy).releaseConfirmedNotSentWritingAgentReservation(user, new AiUsageContext("WRITING_AGENT", run.toString(), "step:3"));
        verifyNoInteractions(ai, wire);
        verify(store, never()).result(any(), any(), any());
        verify(economy, never()).releaseAiReservation(any(), any());
    }

    @Test
    void uncertainOrUnrelatedRecoveryFailureCannotReleaseOrInferAgain() {
        var uncertain = new AiResultUncertainException();
        var changed = new AiRequestNotSentException(HttpStatus.CONFLICT, "WRITING_AGENT_REQUEST_CHANGED");
        when(money.recoverIfPresent(run, "control:2", wire)).thenThrow(uncertain).thenThrow(changed);
        try (var ignored = AiSelectionContext.install(new AiSelectionContext.Snapshot("agent-run", Map.of(selected.featureKey(), selected)))) {
            assertSame(uncertain, assertThrows(AiResultUncertainException.class, () -> service.model(user, run, 3, "control:2", request)));
            assertSame(changed, assertThrows(AiRequestNotSentException.class, () -> service.model(user, run, 3, "control:2", request)));
        }
        verifyNoInteractions(economy, ai, wire);
        verify(store, never()).result(any(), any(), any());
    }
}
