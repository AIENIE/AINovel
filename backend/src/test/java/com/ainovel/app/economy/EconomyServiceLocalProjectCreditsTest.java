package com.ainovel.app.economy;

import com.ainovel.app.economy.model.CreditLedgerType;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.economy.model.RedeemCode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EconomyServiceLocalProjectCreditsTest {

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
    void grantProjectCredits_shouldWriteLocalAccountAndLedgerWithoutPayServiceProjectGrant() {
        User user = user();
        when(accountRepository.findForUpdateByUserId(user.getId())).thenReturn(Optional.empty());
        when(accountRepository.save(any(ProjectCreditAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerRepository.save(any(ProjectCreditLedger.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(billingGrpcClient.publicBalance(42L)).thenReturn(30L);

        EconomyService.CreditChangeResult result = economyService.grantProjectCredits(user, 25L, "manual", "admin");

        assertTrue(result.success());
        assertEquals(525L, result.projectCredits());
        assertEquals(555L, result.totalCredits());
        verify(billingGrpcClient, never()).grantProjectCredits(anyLong(), anyLong(), any(String.class));

        ArgumentCaptor<ProjectCreditLedger> ledger = ArgumentCaptor.forClass(ProjectCreditLedger.class);
        verify(ledgerRepository).save(ledger.capture());
        assertEquals(CreditLedgerType.ADMIN_GRANT, ledger.getValue().getEntryType());
        assertEquals(25L, ledger.getValue().getDelta());
        assertEquals(525L, ledger.getValue().getBalanceAfter());
    }

    @Test
    void listRedeemCodes_shouldReadLocalRedeemCodes() {
        RedeemCode code = new RedeemCode();
        code.setId(UUID.randomUUID());
        code.setCode("LOCAL-100");
        code.setGrantAmount(100L);
        code.setMaxUses(3);
        code.setUsedCount(1);
        code.setEnabled(true);
        code.setStackable(false);
        when(redeemCodeRepository.findAll()).thenReturn(List.of(code));

        List<EconomyService.RedeemCodeView> result = economyService.listRedeemCodes();

        assertEquals(1, result.size());
        assertEquals("LOCAL-100", result.getFirst().code());
        assertEquals(100L, result.getFirst().grantAmount());
        assertEquals(1, result.getFirst().usedCount());
    }

    @Test
    void listLedger_shouldReadLocalProjectLedger() {
        User user = user();
        ProjectCreditLedger ledger = new ProjectCreditLedger();
        ledger.setId(UUID.randomUUID());
        ledger.setUser(user);
        ledger.setEntryType(CreditLedgerType.REDEEM_CODE);
        ledger.setDelta(100L);
        ledger.setBalanceAfter(100L);
        ledger.setReferenceType("REDEEM_CODE");
        ledger.setReferenceId("LOCAL-100");
        ledger.setDescription("兑换码");
        when(ledgerRepository.findByOrderByCreatedAtDesc(any())).thenReturn(new PageImpl<>(List.of(ledger)));

        var result = economyService.listLedger(PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals("REDEEM_CODE", result.getContent().getFirst().type());
        assertEquals(100L, result.getContent().getFirst().delta());
        assertEquals("alice", result.getContent().getFirst().username());
    }

    private User user() {
        User user = new User();
        user.setId(UUID.fromString("0f41d89f-e04f-47e2-aa87-c2bf9a29fd0f"));
        user.setUsername("alice");
        user.setEmail("alice@example.com");
        user.setPasswordHash("hash");
        user.setRemoteUid(42L);
        return user;
    }
}
