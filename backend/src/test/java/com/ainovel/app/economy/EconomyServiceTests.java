package com.ainovel.app.economy;

import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.economy.repo.RedeemCodeRepository;
import com.ainovel.app.economy.repo.RedeemCodeUsageRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    @Mock
    private ProjectCreditAccountRepository accountRepository;
    @Mock
    private ProjectCreditLedgerRepository ledgerRepository;
    @Mock
    private RedeemCodeRepository redeemCodeRepository;
    @Mock
    private RedeemCodeUsageRepository usageRepository;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(billingGrpcClient, userRepository, conversionOrderRepository);
        ReflectionTestUtils.setField(economyService, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(economyService, "ledgerRepository", ledgerRepository);
        ReflectionTestUtils.setField(economyService, "redeemCodeRepository", redeemCodeRepository);
        ReflectionTestUtils.setField(economyService, "redeemCodeUsageRepository", usageRepository);
    }

    @Test
    void checkIn_shouldBeDisabledInAinovel() {
        assertThrows(IllegalStateException.class, () -> economyService.checkIn(user()));
    }

    @Test
    void currentBalance_shouldCombineLocalProjectBalanceWithPayServicePublicBalance() {
        User user = user();
        ProjectCreditAccount account = account(user, 120L);
        when(accountRepository.findByUser(user)).thenReturn(Optional.of(account));
        when(billingGrpcClient.publicBalance(42L)).thenReturn(30L);

        EconomyService.BalanceSnapshot result = economyService.currentBalance(user);

        assertEquals(120L, result.projectCredits());
        assertEquals(30L, result.publicCredits());
        assertEquals(150L, result.totalCredits());
    }

    @Test
    void chargeAiUsage_shouldDebitLocalProjectAccountWithoutPayServiceUsageDeduction() {
        User user = user();
        ProjectCreditAccount account = account(user, 3L);
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));

        EconomyService.AiChargeResult result = economyService.chargeAiUsage(user, 100_000L, 0L, "ai-1");

        assertEquals(1L, result.charged());
        assertEquals(2L, result.remainingProjectCredits());
        verify(billingGrpcClient, never()).deductUsage(anyLong(), anyLong(), anyLong(), any(String.class));
        verify(ledgerRepository).save(any(ProjectCreditLedger.class));
    }

    @Test
    void convert_shouldDeductPublicCreditsInPayServiceThenCreditLocalProjectAccount() {
        User user = user();
        ProjectCreditAccount account = account(user, 100L);
        when(conversionOrderRepository.findByUserAndIdempotencyKey(user, "k1")).thenReturn(Optional.empty());
        when(accountRepository.findByUser(user)).thenReturn(Optional.of(account));
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));
        when(billingGrpcClient.publicBalance(42L)).thenReturn(80L);
        when(billingGrpcClient.convertPublicToProject(eq(42L), eq(30L), any()))
                .thenReturn(new BillingGrpcClient.ConversionResult(true, 30L, 30L, 50L, null));
        when(conversionOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        EconomyService.ConversionResult result = economyService.convertPublicToProject(user, 30L, "k1");

        assertEquals(30L, result.amount());
        assertEquals(100L, result.projectBefore());
        assertEquals(130L, result.projectAfter());
        assertEquals(80L, result.publicBefore());
        assertEquals(50L, result.publicAfter());
    }

    @Test
    void listLedger_shouldReadLocalLedger() {
        User user = user();
        ProjectCreditLedger ledger = new ProjectCreditLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setUser(user);
        ledger.setDelta(-1L);
        ledger.setBalanceAfter(99L);
        ledger.setEntryType(com.ainovel.app.economy.model.CreditLedgerType.AI_DEBIT);
        when(ledgerRepository.findByUserOrderByCreatedAtDesc(eq(user), any()))
                .thenReturn(new PageImpl<>(List.of(ledger)));

        var page = economyService.listLedger(user, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals("AI_DEBIT", page.getContent().getFirst().type());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.fromString("0f41d89f-e04f-47e2-aa87-c2bf9a29fd0f"));
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(42L);
        user.setCredits(100L);
        return user;
    }

    private ProjectCreditAccount account(User user, long balance) {
        ProjectCreditAccount account = new ProjectCreditAccount();
        account.setUser(user);
        account.setBalance(balance);
        return account;
    }
}
