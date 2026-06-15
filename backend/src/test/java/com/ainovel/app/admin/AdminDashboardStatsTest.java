package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.AdminDashboardStatsResponse;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import com.ainovel.app.economy.repo.ProjectCreditLedgerRepository;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.metrics.ApiRequestMetrics;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.world.repo.WorldRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminDashboardStatsTest {

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

        AdminController controller = new AdminController(
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

        ResponseEntity<AdminDashboardStatsResponse> response = controller.dashboard();

        AdminDashboardStatsResponse body = response.getBody();
        assertEquals(7L, body.totalUsers());
        assertEquals(2L, body.todayNewUsers());
        assertEquals(45.0, body.totalCreditsConsumed());
        assertEquals(12.0, body.todayCreditsConsumed());
        assertEquals(0.5, body.apiErrorRate());
        assertEquals(3L, body.pendingReviews());
    }
}
