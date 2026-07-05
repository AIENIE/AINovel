package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.AdminDashboardStatsResponse;
import com.ainovel.app.admin.dto.AdminSystemConfigResponse;
import com.ainovel.app.admin.dto.AdminUserDto;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.metrics.ApiRequestMetrics;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminConsoleServiceTest {

    @Test
    void dashboardShouldUseLedgerAndRequestMetricsInsteadOfFixedZeros() {
        UserRepository userRepository = mock(UserRepository.class);
        MaterialRepository materialRepository = mock(MaterialRepository.class);
        ProjectCreditLedgerRepository ledgerRepository = mock(ProjectCreditLedgerRepository.class);
        ApiRequestMetrics requestMetrics = new ApiRequestMetrics();
        requestMetrics.record(200);
        requestMetrics.record(500);

        when(userRepository.count()).thenReturn(7L);
        when(userRepository.countByCreatedAtAfter(any(Instant.class))).thenReturn(2L);
        when(materialRepository.countByStatusIgnoreCase("pending")).thenReturn(3L);
        when(ledgerRepository.sumNegativeDeltaAbs()).thenReturn(45L);
        when(ledgerRepository.sumNegativeDeltaAbsSince(any(Instant.class))).thenReturn(12L);

        AdminConsoleService service = new AdminConsoleService(
                userRepository,
                materialRepository,
                mock(SettingsService.class),
                mock(GlobalSettingsRepository.class),
                mock(EconomyService.class),
                ledgerRepository,
                mock(ProjectCreditAccountRepository.class),
                mock(StoryRepository.class),
                mock(WorldRepository.class),
                requestMetrics
        );

        AdminDashboardStatsResponse response = service.dashboard();

        assertEquals(7L, response.totalUsers());
        assertEquals(2L, response.todayNewUsers());
        assertEquals(45.0, response.totalCreditsConsumed());
        assertEquals(12.0, response.todayCreditsConsumed());
        assertEquals(0.5, response.apiErrorRate());
        assertEquals(3L, response.pendingReviews());
    }

    @Test
    void usersShouldFilterAndBuildSnapshots() {
        UserRepository userRepository = mock(UserRepository.class);
        ProjectCreditAccountRepository accountRepository = mock(ProjectCreditAccountRepository.class);
        StoryRepository storyRepository = mock(StoryRepository.class);
        WorldRepository worldRepository = mock(WorldRepository.class);
        EconomyService economyService = mock(EconomyService.class);

        User alice = user("alice", "alice@example.com", 1001L, Set.of("ROLE_ADMIN"));
        alice.setCredits(500);
        User bob = user("bob", "bob@example.com", 1002L, Set.of("ROLE_USER"));
        bob.setCredits(320);
        when(userRepository.findAll()).thenReturn(java.util.List.of(alice, bob));

        ProjectCreditAccount aliceAccount = new ProjectCreditAccount();
        aliceAccount.setUser(alice);
        aliceAccount.setBalance(700L);
        when(accountRepository.findByUser(alice)).thenReturn(Optional.of(aliceAccount));
        when(accountRepository.findByUser(bob)).thenReturn(Optional.empty());
        when(economyService.currentBalance(alice)).thenReturn(new EconomyService.BalanceSnapshot(700L, 40L, 740L));
        when(economyService.currentBalance(bob)).thenThrow(new RuntimeException("remote unavailable"));
        when(storyRepository.countByUser(alice)).thenReturn(2L);
        when(storyRepository.countByUser(bob)).thenReturn(1L);
        when(worldRepository.countByUser(alice)).thenReturn(1L);
        when(worldRepository.countByUser(bob)).thenReturn(0L);

        AdminConsoleService service = new AdminConsoleService(
                userRepository,
                mock(MaterialRepository.class),
                mock(SettingsService.class),
                mock(GlobalSettingsRepository.class),
                economyService,
                mock(ProjectCreditLedgerRepository.class),
                accountRepository,
                storyRepository,
                worldRepository,
                new ApiRequestMetrics()
        );

        java.util.List<AdminUserDto> result = service.users("ali");

        assertEquals(1, result.size());
        AdminUserDto aliceDto = result.getFirst();
        assertEquals(alice.getId().toString(), aliceDto.id());
        assertEquals("admin", aliceDto.role());
        assertEquals(700L, aliceDto.projectCredits());
        assertEquals(40L, aliceDto.publicCredits());
        assertEquals(740L, aliceDto.totalCredits());
        assertEquals(2L, aliceDto.storyCount());
        assertEquals(1L, aliceDto.worldCount());
    }

    @Test
    void updateSystemConfigShouldPersistMaintenanceMode() {
        SettingsService settingsService = mock(SettingsService.class);
        GlobalSettingsRepository globalSettingsRepository = mock(GlobalSettingsRepository.class);
        GlobalSettings settings = new GlobalSettings();
        settings.setMaintenanceMode(false);
        when(settingsService.getGlobalSettings()).thenReturn(settings);
        when(globalSettingsRepository.save(settings)).thenReturn(settings);

        AdminConsoleService service = new AdminConsoleService(
                mock(UserRepository.class),
                mock(MaterialRepository.class),
                settingsService,
                globalSettingsRepository,
                mock(EconomyService.class),
                mock(ProjectCreditLedgerRepository.class),
                mock(ProjectCreditAccountRepository.class),
                mock(StoryRepository.class),
                mock(WorldRepository.class),
                new ApiRequestMetrics()
        );

        AdminSystemConfigResponse response = service.updateSystemConfig(true);

        assertTrue(response.maintenanceMode());
        assertTrue(settings.isMaintenanceMode());
        verify(globalSettingsRepository).save(settings);
    }

    @Test
    void resolveTargetUserShouldSupportUuidAndRemoteUid() {
        UserRepository userRepository = mock(UserRepository.class);
        User target = user("alice", "alice@example.com", 1001L, Set.of("ROLE_USER"));
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findByRemoteUid(1001L)).thenReturn(Optional.of(target));

        AdminConsoleService service = new AdminConsoleService(
                userRepository,
                mock(MaterialRepository.class),
                mock(SettingsService.class),
                mock(GlobalSettingsRepository.class),
                mock(EconomyService.class),
                mock(ProjectCreditLedgerRepository.class),
                mock(ProjectCreditAccountRepository.class),
                mock(StoryRepository.class),
                mock(WorldRepository.class),
                new ApiRequestMetrics()
        );

        assertSame(target, service.resolveTargetUser(target.getId().toString()));
        assertSame(target, service.resolveTargetUser("1001"));
    }

    private User user(String username, String email, long remoteUid, Set<String> roles) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setRemoteUid(remoteUid);
        user.setRoles(roles);
        user.setCreatedAt(Instant.parse("2026-07-01T00:00:00Z"));
        user.setUpdatedAt(Instant.parse("2026-07-02T00:00:00Z"));
        return user;
    }
}
