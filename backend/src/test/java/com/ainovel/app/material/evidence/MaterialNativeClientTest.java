package com.ainovel.app.material.evidence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import com.ainovel.app.user.User;

import org.junit.jupiter.api.Test;

import java.util.List;

class MaterialNativeClientTest {
    @org.junit.jupiter.api.Test
    void onlyAuthoritativeNoEgressBudgetRefusalCanBeAutomaticallyQueued() {
        assertTrue(
                MaterialNativeClient.budgetBusy(
                        io.grpc.Status.FAILED_PRECONDITION
                                .withDescription("RETRIEVAL_BUDGET_IN_FLIGHT")
                                .asRuntimeException()));
        assertFalse(
                MaterialNativeClient.budgetBusy(
                        io.grpc.Status.UNAVAILABLE
                                .withDescription("RETRIEVAL_BUDGET_IN_FLIGHT")
                                .asRuntimeException()));
        assertFalse(
                MaterialNativeClient.budgetBusy(
                        io.grpc.Status.FAILED_PRECONDITION
                                .withDescription("RETRIEVAL_RESULT_RECONCILIATION_REQUIRED")
                                .asRuntimeException()));
        assertFalse(
                MaterialNativeClient.budgetBusy(
                        io.grpc.Status.DEADLINE_EXCEEDED.asRuntimeException()));
    }

    @Test
    void recoveryKeepsOriginalRunWhenConfigurationChanges() {
        var gateway = mock(AiGatewayGrpcClient.class);
        var client = new MaterialNativeClient(gateway, "replacement-run");
        var user = new User();
        user.setRemoteUid(81L);
        var messages = List.of(new AiChatRequest.Message("user", "核对日落条件"));
        client.structured(user, "original-key", messages, "{}", 4096, "original-run");
        verify(gateway).structured("original-key", 81L, messages, "{}", 4096, "original-run");
    }

    @Test
    void recoveryCannotChangeGatewayUserOrGuessLegacyIdentity() {
        var user = new User();
        user.setRemoteUid(81L);
        MaterialNativeClient.requireOriginalUser(user, 81L);
        assertThrows(
                IllegalStateException.class,
                () -> MaterialNativeClient.requireOriginalUser(user, 82L));
        assertThrows(
                IllegalStateException.class,
                () -> MaterialNativeClient.requireOriginalUser(user, null));
    }
}
