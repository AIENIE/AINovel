package com.ainovel.app.admin;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.admin.dto.AdminDashboardStatsResponse;
import com.ainovel.app.admin.dto.AdminSystemConfigResponse;
import com.ainovel.app.admin.dto.AdminUserDto;
import com.ainovel.app.economy.EconomyService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class AdminConsoleService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final SettingsService settingsService;
    private final GlobalSettingsRepository globalSettingsRepository;
    private final EconomyService economyService;
    private final ProjectCreditLedgerRepository projectCreditLedgerRepository;
    private final ProjectCreditAccountRepository projectCreditAccountRepository;
    private final StoryRepository storyRepository;
    private final WorldRepository worldRepository;
    private final ApiRequestMetrics apiRequestMetrics;

    public AdminConsoleService(
            UserRepository userRepository,
            MaterialRepository materialRepository,
            SettingsService settingsService,
            GlobalSettingsRepository globalSettingsRepository,
            EconomyService economyService,
            ProjectCreditLedgerRepository projectCreditLedgerRepository,
            ProjectCreditAccountRepository projectCreditAccountRepository,
            StoryRepository storyRepository,
            WorldRepository worldRepository,
            ApiRequestMetrics apiRequestMetrics
    ) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.settingsService = settingsService;
        this.globalSettingsRepository = globalSettingsRepository;
        this.economyService = economyService;
        this.projectCreditLedgerRepository = projectCreditLedgerRepository;
        this.projectCreditAccountRepository = projectCreditAccountRepository;
        this.storyRepository = storyRepository;
        this.worldRepository = worldRepository;
        this.apiRequestMetrics = apiRequestMetrics;
    }

    @Transactional(readOnly = true)
    public AdminDashboardStatsResponse dashboard() {
        Instant todayStart = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        long totalUsers = userRepository.count();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStart);
        double totalConsumed = projectCreditLedgerRepository.sumNegativeDeltaAbs();
        double todayConsumed = projectCreditLedgerRepository.sumNegativeDeltaAbsSince(todayStart);
        long pendingReviews = materialRepository.countByStatusIgnoreCase("pending");
        double apiErrorRate = apiRequestMetrics.errorRate();
        return new AdminDashboardStatsResponse(
                totalUsers,
                todayNewUsers,
                totalConsumed,
                todayConsumed,
                apiErrorRate,
                pendingReviews
        );
    }

    @Transactional(readOnly = true)
    public List<AdminUserDto> users(String search) {
        String keyword = search == null ? "" : search.trim().toLowerCase();
        return userRepository.findAll().stream()
                .filter(user -> keyword.isBlank()
                        || safe(user.getUsername()).toLowerCase().contains(keyword)
                        || safe(user.getEmail()).toLowerCase().contains(keyword))
                .map(this::toUserDto)
                .toList();
    }

    @Transactional
    public AdminSystemConfigResponse systemConfig() {
        GlobalSettings settings = settingsService.getGlobalSettings();
        return new AdminSystemConfigResponse(settings.isMaintenanceMode());
    }

    @Transactional
    public AdminSystemConfigResponse updateSystemConfig(Boolean maintenanceMode) {
        GlobalSettings settings = settingsService.getGlobalSettings();
        if (maintenanceMode != null) {
            settings.setMaintenanceMode(maintenanceMode);
        }
        GlobalSettings saved = globalSettingsRepository.save(settings);
        return new AdminSystemConfigResponse(saved.isMaintenanceMode());
    }

    @Transactional(readOnly = true)
    public User resolveTargetUser(String userId) {
        String value = userId == null ? "" : userId.trim();
        if (value.isBlank()) {
            throw new BusinessException("目标用户不能为空");
        }
        try {
            return userRepository.findById(UUID.fromString(value))
                    .orElseThrow(() -> new BusinessException("用户不存在"));
        } catch (IllegalArgumentException ignore) {
            try {
                long remoteUid = Long.parseLong(value);
                return userRepository.findByRemoteUid(remoteUid)
                        .orElseThrow(() -> new BusinessException("用户不存在"));
            } catch (NumberFormatException ex) {
                throw new BusinessException("用户 ID 格式错误");
            }
        }
    }

    private AdminUserDto toUserDto(User local) {
        long projectCredits = projectCreditAccountRepository.findByUser(local)
                .map(com.ainovel.app.economy.model.ProjectCreditAccount::getBalance)
                .orElse(Math.round(local.getCredits()));
        long publicCredits = 0L;
        try {
            publicCredits = economyService.currentBalance(local).publicCredits();
        } catch (RuntimeException ignored) {
            publicCredits = 0L;
        }
        return new AdminUserDto(
                String.valueOf(local.getId()),
                local.getRemoteUid(),
                local.getUsername(),
                local.getEmail(),
                local.hasRole("ROLE_ADMIN") ? "admin" : "user",
                projectCredits,
                publicCredits,
                projectCredits + publicCredits,
                storyRepository.countByUser(local),
                worldRepository.countByUser(local),
                local.isBanned(),
                local.getCreatedAt(),
                local.getUpdatedAt()
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
