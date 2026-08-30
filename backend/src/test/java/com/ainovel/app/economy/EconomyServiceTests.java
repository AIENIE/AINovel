package com.ainovel.app.economy;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.model.AiCreditReservation;
import com.ainovel.app.economy.repo.AiCreditReservationRepository;
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
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    @Mock
    private AiCreditReservationRepository aiReservationRepository;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(billingGrpcClient, userRepository, conversionOrderRepository);
        ReflectionTestUtils.setField(economyService, "accountRepository", accountRepository);
        ReflectionTestUtils.setField(economyService, "ledgerRepository", ledgerRepository);
        ReflectionTestUtils.setField(economyService, "redeemCodeRepository", redeemCodeRepository);
        ReflectionTestUtils.setField(economyService, "redeemCodeUsageRepository", usageRepository);
        ReflectionTestUtils.setField(economyService, "aiReservationRepository", aiReservationRepository);
        TransactionTemplate transactions = org.mockito.Mockito.mock(TransactionTemplate.class);
        org.mockito.Mockito.lenient().when(transactions.execute(any())).thenAnswer(invocation ->
                ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        ReflectionTestUtils.setField(economyService, "transactions", transactions);
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
    void currentBalance_shouldLogFixedFailureEventWithSafeThrowable() {
        User user = user();
        when(accountRepository.findByUser(user)).thenReturn(Optional.empty());
        when(billingGrpcClient.publicBalance(42L))
                .thenThrow(new IllegalStateException("sensitive upstream response"));
        when(conversionOrderRepository.findFirstByUserOrderByCreatedAtDesc(user)).thenReturn(Optional.empty());
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(EconomyService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        EconomyService.BalanceSnapshot result;
        try {
            result = economyService.currentBalance(user);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertEquals(0L, result.publicCredits());
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(
                "event=public_balance_fetch_failed remoteUid=42 fallback=0 errorType=IllegalStateException",
                event.getFormattedMessage()
        );
        assertFalse(event.getFormattedMessage().contains("sensitive upstream response"));
        assertNotNull(event.getThrowableProxy());
        assertEquals("com.ainovel.app.common.SafeLogThrowable", event.getThrowableProxy().getClassName());
        assertNull(event.getThrowableProxy().getMessage());
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
    void aiReservation_shouldDebitBeforeCallSettleActualUsageAndReplayWithoutAnotherDebit() {
        User user = user();
        ProjectCreditAccount account = account(user, 10L);
        java.util.concurrent.atomic.AtomicReference<AiCreditReservation> saved = new java.util.concurrent.atomic.AtomicReference<>();
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));
        when(aiReservationRepository.findByUserAndIdempotencyKey(user, "request-1"))
                .thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(aiReservationRepository.save(any())).thenAnswer(invocation -> {
            AiCreditReservation reservation = invocation.getArgument(0);
            saved.set(reservation);
            return reservation;
        });

        EconomyService.AiReservationStart started = economyService.reserveAiUsage(
                user, 2L, "AI_USAGE", "request-1", "request-1", "hash-1");
        assertFalse(started.replay());
        assertEquals(8L, account.getBalance());

        EconomyService.AiChargeResult settled = economyService.settleAiUsage(
                user, "request-1", "result", 100_000L, 0L, 0L);
        assertEquals(1L, settled.charged());
        assertEquals(9L, account.getBalance());

        EconomyService.AiReservationStart replay = economyService.reserveAiUsage(
                user, 2L, "AI_USAGE", "request-1", "request-1", "hash-1");
        assertEquals(true, replay.replay());
        assertEquals("result", replay.content());
        assertEquals(9L, account.getBalance());
    }

    @Test
    void convert_shouldDeductPublicCreditsInPayServiceThenCreditLocalProjectAccount() {
        User user = user();
        ProjectCreditAccount account = account(user, 100L);
        when(conversionOrderRepository.findByUserAndIdempotencyKey(user, "k1")).thenReturn(Optional.empty());
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));
        when(billingGrpcClient.publicBalance(42L)).thenReturn(80L);
        when(billingGrpcClient.convertPublicToProject(eq(42L), eq(30L), any()))
                .thenReturn(new BillingGrpcClient.ConversionResult(true, 30L, 30L, 50L, null));
        java.util.concurrent.atomic.AtomicReference<com.ainovel.app.economy.model.CreditConversionOrder> savedOrder =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(conversionOrderRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            com.ainovel.app.economy.model.CreditConversionOrder order = invocation.getArgument(0);
            order.setId(UUID.randomUUID());
            savedOrder.set(order);
            return order;
        });
        when(conversionOrderRepository.findForUpdateById(any())).thenAnswer(invocation ->
                Optional.ofNullable(savedOrder.get()));

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
