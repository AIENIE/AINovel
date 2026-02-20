package com.ainovel.app.economy;

import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.RedeemCode;
import com.ainovel.app.economy.repo.CheckInRecordRepository;
import com.ainovel.app.economy.repo.CreditConversionOrderRepository;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.economy.repo.RedeemCodeRepository;
import com.ainovel.app.economy.repo.RedeemCodeUsageRepository;
import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTests {

    @Mock
    private BillingGrpcClient billingGrpcClient;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectCreditAccountRepository accountRepository;
    @Mock
    private ProjectCreditLedgerRepository ledgerRepository;
    @Mock
    private CheckInRecordRepository checkInRecordRepository;
    @Mock
    private RedeemCodeRepository redeemCodeRepository;
    @Mock
    private RedeemCodeUsageRepository redeemCodeUsageRepository;
    @Mock
    private CreditConversionOrderRepository conversionOrderRepository;
    @Mock
    private SettingsService settingsService;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        PlatformTransactionManager transactionManager = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
        economyService = new EconomyService(
                billingGrpcClient,
                userRepository,
                accountRepository,
                ledgerRepository,
                checkInRecordRepository,
                redeemCodeRepository,
                redeemCodeUsageRepository,
                conversionOrderRepository,
                settingsService,
                transactionManager
        );
    }

    @Test
    void checkIn_shouldGrantLocalCredits() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setCredits(100);

        ProjectCreditAccount account = new ProjectCreditAccount();
        account.setUser(user);
        account.setBalance(100);

        GlobalSettings settings = new GlobalSettings();
        settings.setCheckInMinPoints(10);
        settings.setCheckInMaxPoints(10);

        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));
        when(checkInRecordRepository.existsByUserAndCheckinDate(eq(user), any(LocalDate.class))).thenReturn(false);
        when(settingsService.getGlobalSettings()).thenReturn(settings);

        EconomyService.CreditChangeResult result = economyService.checkIn(user);

        assertTrue(result.success());
        assertEquals(10, result.points());
        assertEquals(110, result.projectCredits());
        verify(accountRepository).save(any(ProjectCreditAccount.class));
        verify(ledgerRepository).save(any());
        verify(checkInRecordRepository).save(any());
    }

    @Test
    void redeem_shouldThrowWhenCodeNotFound() {
        User user = new User();
        when(redeemCodeRepository.findByCode("INVALID")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> economyService.redeem(user, "INVALID"));
        assertEquals("兑换码无效", ex.getMessage());
    }

    @Test
    void redeem_shouldApplyCodeWhenValid() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setCredits(80);

        RedeemCode code = new RedeemCode();
        code.setCode("VIP888");
        code.setGrantAmount(50);
        code.setEnabled(true);
        code.setStackable(true);
        code.setUsedCount(0);

        ProjectCreditAccount account = new ProjectCreditAccount();
        account.setUser(user);
        account.setBalance(80);

        when(redeemCodeRepository.findByCode("VIP888")).thenReturn(Optional.of(code));
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));

        EconomyService.CreditChangeResult result = economyService.redeem(user, "vip888");

        assertEquals(50, result.points());
        assertEquals(130, result.projectCredits());
        verify(redeemCodeRepository).save(any(RedeemCode.class));
        verify(redeemCodeUsageRepository).save(any());
        verify(ledgerRepository).save(any());
    }

    @Test
    void convert_shouldThrowWhenRemoteUidMissing() {
        User user = new User();
        user.setId(UUID.randomUUID());

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> economyService.convertPublicToProject(user, 100, "k1")
        );
        assertEquals("当前账号未绑定统一用户，无法兑换通用积分", ex.getMessage());
    }

    @Test
    void chargeAiUsage_shouldRejectInsufficientBalance() {
        User user = new User();
        user.setId(UUID.randomUUID());

        ProjectCreditAccount account = new ProjectCreditAccount();
        account.setUser(user);
        account.setBalance(0);

        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.of(account));

        RuntimeException ex = assertThrows(
                RuntimeException.class,
                () -> economyService.chargeAiUsage(user, 100_000, 0, "ai-1")
        );
        assertEquals("项目积分不足，请先兑换项目积分", ex.getMessage());
    }
}
