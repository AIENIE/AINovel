package com.ainovel.app.economy;

import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTests {

    @Mock
    private BillingGrpcClient billingGrpcClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CreditConversionOrderRepository conversionOrderRepository;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(
                billingGrpcClient,
                userRepository,
                conversionOrderRepository
        );
    }

    @Test
    void checkIn_shouldDelegateToPayServiceWithoutLocalLedgerWrites() {
        User user = remoteUser();
        when(billingGrpcClient.checkin(42L))
                .thenReturn(new BillingGrpcClient.CheckinResult(true, 10, 160, false, "CHECKIN_SUCCESS"));
        when(billingGrpcClient.projectBalance(42L)).thenReturn(110L);
        when(billingGrpcClient.publicBalance(42L)).thenReturn(50L);

        EconomyService.CreditChangeResult result = economyService.checkIn(user);

        assertTrue(result.success());
        assertEquals(10, result.points());
        assertEquals(110, result.projectCredits());
        assertEquals(50, result.publicCredits());
        assertEquals(160, result.totalCredits());
    }

    @Test
    void redeem_shouldDelegateToPayServiceWithoutLocalRedeemCodeWrites() {
        User user = remoteUser();
        when(billingGrpcClient.redeem(42L, "VIP888"))
                .thenReturn(new BillingGrpcClient.RedeemResult(true, 50, 210, null));
        when(billingGrpcClient.projectBalance(42L)).thenReturn(160L);
        when(billingGrpcClient.publicBalance(42L)).thenReturn(50L);

        EconomyService.CreditChangeResult result = economyService.redeem(user, "vip888");

        assertEquals(50, result.points());
        assertEquals(160, result.projectCredits());
        assertEquals(210, result.totalCredits());
    }

    @Test
    void convert_shouldUsePayServiceBalancesWithoutLocalAccountWrites() {
        User user = remoteUser();
        when(billingGrpcClient.projectBalance(42L)).thenReturn(100L, 130L);
        when(billingGrpcClient.publicBalance(42L)).thenReturn(80L, 50L);
        when(billingGrpcClient.convertPublicToProject(eq(42L), eq(30L), any()))
                .thenReturn(new BillingGrpcClient.ConversionResult(true, 30, 30, 50, null));

        EconomyService.ConversionResult result = economyService.convertPublicToProject(user, 30, "k1");

        assertEquals(30, result.amount());
        assertEquals(100, result.projectBefore());
        assertEquals(130, result.projectAfter());
        assertEquals(80, result.publicBefore());
        assertEquals(50, result.publicAfter());
        assertEquals(180, result.totalCredits());
    }

    @Test
    void chargeAiUsage_shouldDeductThroughPayService() {
        User user = remoteUser();
        when(billingGrpcClient.deductUsage(eq(42L), eq(1L), eq(0L), eq("ai-1")))
                .thenReturn(new BillingGrpcClient.UsageDeductionResult(1L, 99L));

        EconomyService.AiChargeResult result = economyService.chargeAiUsage(user, 100_000, 0, "ai-1");

        assertEquals(1L, result.charged());
        assertEquals(99L, result.remainingProjectCredits());
    }

    @Test
    void listLedger_shouldReadPayServiceLedger() {
        User user = remoteUser();
        Instant createdAt = Instant.parse("2026-06-03T01:02:03Z");
        when(billingGrpcClient.listLedgerEntries(42L, 0, 20))
                .thenReturn(new BillingGrpcClient.LedgerPage(
                        java.util.List.of(new BillingGrpcClient.LedgerEntryView(
                                "1001",
                                "AI_DEBIT",
                                -1L,
                                99L,
                                "pay-service",
                                "ai-1",
                                "AI_DEBIT",
                                createdAt
                        )),
                        1L
                ));

        var page = economyService.listLedger(user, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("AI_DEBIT", page.getContent().getFirst().type());
        assertEquals(-1L, page.getContent().getFirst().delta());
        assertEquals(99L, page.getContent().getFirst().balanceAfter());
    }

    @Test
    void remoteUidMissing_shouldFailBeforeCallingPayService() {
        User user = new User();
        user.setId(UUID.randomUUID());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> economyService.projectBalance(user));

        assertEquals("当前账号未绑定统一用户，无法查询项目积分", ex.getMessage());
        verify(billingGrpcClient, never()).projectBalance(anyLong());
    }

    private User remoteUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setRemoteUid(42L);
        user.setCredits(0);
        return user;
    }
}
