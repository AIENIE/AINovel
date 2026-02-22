package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.*;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.integration.UserAdminRemoteClient;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.settings.SettingsService;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import com.ainovel.app.settings.model.GlobalSettings;
import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "管理端接口（聚合第三方服务 + 本地管理能力）")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private final UserRepository userRepository;
    private final MaterialRepository materialRepository;
    private final UserAdminRemoteClient userAdminRemoteClient;
    private final SettingsService settingsService;
    private final GlobalSettingsRepository globalSettingsRepository;
    private final EconomyService economyService;

    public AdminController(
            UserRepository userRepository,
            MaterialRepository materialRepository,
            UserAdminRemoteClient userAdminRemoteClient,
            SettingsService settingsService,
            GlobalSettingsRepository globalSettingsRepository,
            EconomyService economyService
    ) {
        this.userRepository = userRepository;
        this.materialRepository = materialRepository;
        this.userAdminRemoteClient = userAdminRemoteClient;
        this.settingsService = settingsService;
        this.globalSettingsRepository = globalSettingsRepository;
        this.economyService = economyService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取管理看板", description = "返回用户规模、今日新增、待审核素材、消费与错误率等汇总指标。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public ResponseEntity<AdminDashboardStatsResponse> dashboard() {
        Instant todayStart = LocalDate.now(ZONE).atStartOfDay(ZONE).toInstant();
        long totalUsers = userRepository.count();
        long todayNewUsers = userRepository.countByCreatedAtAfter(todayStart);
        double totalConsumed = 0.0;
        double todayConsumed = 0.0;
        long pendingReviews = materialRepository.countByStatusIgnoreCase("pending");
        double apiErrorRate = 0.0;
        return ResponseEntity.ok(new AdminDashboardStatsResponse(totalUsers, todayNewUsers, totalConsumed, todayConsumed, apiErrorRate, pendingReviews));
    }

    @GetMapping("/users")
    @Operation(summary = "查询用户列表", description = "透传 UserService 管理端用户列表，支持关键字模糊查询。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public List<AdminUserDto> users(
            @Parameter(description = "管理员 JWT，透传给 UserService", example = "Bearer eyJhbGciOi...")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "用户名/邮箱关键字", example = "demo")
            @RequestParam(value = "search", required = false) String search
    ) {
        return userAdminRemoteClient.listUsers(authorization, search).stream()
                .map(this::toUserDto)
                .toList();
    }

    @PostMapping("/users/{id}/ban")
    @Operation(summary = "封禁用户", description = "调用 UserService 执行用户封禁，并同步本地用户快照状态。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "封禁成功"),
            @ApiResponse(responseCode = "400", description = "请求参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public ResponseEntity<Boolean> ban(
            @Parameter(description = "管理员 JWT，透传给 UserService", example = "Bearer eyJhbGciOi...")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "目标用户 ID（UserService 侧 ID）", example = "1001")
            @PathVariable long id,
            @Parameter(description = "封禁天数", example = "7")
            @RequestParam(value = "days", defaultValue = "7") int days,
            @Parameter(description = "封禁原因", example = "违规行为")
            @RequestParam(value = "reason", defaultValue = "管理员封禁") String reason
    ) {
        userAdminRemoteClient.banUser(authorization, id, days, reason);
        userRepository.findByRemoteUid(id).ifPresent(u -> {
            u.setBanned(true);
            userRepository.save(u);
        });
        return ResponseEntity.ok(true);
    }

    @PostMapping("/users/{id}/unban")
    @Operation(summary = "解封用户", description = "调用 UserService 解除封禁，并同步本地用户快照状态。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "解封成功"),
            @ApiResponse(responseCode = "400", description = "用户不存在"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public ResponseEntity<Boolean> unban(
            @Parameter(description = "管理员 JWT，透传给 UserService", example = "Bearer eyJhbGciOi...")
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Parameter(description = "目标用户 ID（UserService 侧 ID）", example = "1001")
            @PathVariable long id
    ) {
        userAdminRemoteClient.unbanUser(authorization, id);
        userRepository.findByRemoteUid(id).ifPresent(u -> {
            u.setBanned(false);
            userRepository.save(u);
        });
        return ResponseEntity.ok(true);
    }

    @GetMapping("/system-config")
    @Operation(summary = "读取系统配置", description = "返回注册开关、维护开关、签到范围与 SMTP 配置状态。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "读取成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminSystemConfigResponse systemConfig() {
        GlobalSettings g = settingsService.getGlobalSettings();
        return new AdminSystemConfigResponse(
                g.isRegistrationEnabled(),
                g.isMaintenanceMode(),
                g.getCheckInMinPoints(),
                g.getCheckInMaxPoints(),
                g.getSmtpHost(),
                g.getSmtpPort(),
                g.getSmtpUsername(),
                g.getSmtpPassword() != null && !g.getSmtpPassword().isBlank()
        );
    }

    @PutMapping("/system-config")
    @Operation(summary = "更新系统配置", description = "更新注册开关、维护开关、签到范围与 SMTP 配置。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "更新成功",
                    content = @Content(
                            schema = @Schema(implementation = AdminSystemConfigResponse.class),
                            examples = @ExampleObject(value = "{\"registrationEnabled\":true,\"maintenanceMode\":false,\"checkInMinPoints\":10,\"checkInMaxPoints\":50,\"smtpHost\":\"smtp.example.com\",\"smtpPort\":587,\"smtpUsername\":\"noreply@example.com\",\"smtpPasswordIsSet\":true}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminSystemConfigResponse updateSystemConfig(@RequestBody AdminSystemConfigUpdateRequest request) {
        GlobalSettings g = settingsService.getGlobalSettings();
        if (request.registrationEnabled() != null) g.setRegistrationEnabled(request.registrationEnabled());
        if (request.maintenanceMode() != null) g.setMaintenanceMode(request.maintenanceMode());
        if (request.checkInMinPoints() != null) g.setCheckInMinPoints(request.checkInMinPoints());
        if (request.checkInMaxPoints() != null) g.setCheckInMaxPoints(request.checkInMaxPoints());

        if (request.smtpHost() != null) g.setSmtpHost(request.smtpHost());
        if (request.smtpPort() != null) g.setSmtpPort(request.smtpPort());
        if (request.smtpUsername() != null) g.setSmtpUsername(request.smtpUsername());
        if (request.smtpPassword() != null && !request.smtpPassword().isBlank()) g.setSmtpPassword(request.smtpPassword());

        globalSettingsRepository.save(g);
        return systemConfig();
    }

    @PostMapping("/credits/grant")
    @Operation(summary = "管理员加发项目积分", description = "仅支持加分，不支持扣减。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "发放成功"),
            @ApiResponse(responseCode = "400", description = "参数错误或用户不存在"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public ResponseEntity<com.ainovel.app.user.dto.CreditChangeResponse> grantCredits(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AdminGrantCreditsRequest request
    ) {
        User target = resolveTargetUser(request.userId());
        EconomyService.CreditChangeResult result = economyService.grantProjectCredits(
                target,
                request.amount(),
                request.reason(),
                principal == null ? "admin" : principal.getUsername()
        );
        return ResponseEntity.ok(new com.ainovel.app.user.dto.CreditChangeResponse(
                result.success(),
                result.points(),
                result.totalCredits(),
                result.projectCredits(),
                result.publicCredits(),
                result.totalCredits(),
                result.message()
        ));
    }

    @GetMapping("/redeem-codes")
    @Operation(summary = "兑换码列表", description = "查询本地项目兑换码配置。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public List<AdminRedeemCodeDto> listRedeemCodes() {
        return economyService.listRedeemCodes().stream()
                .map(item -> new AdminRedeemCodeDto(
                        item.id(),
                        item.code(),
                        item.grantAmount(),
                        item.maxUses(),
                        item.usedCount(),
                        item.startsAt(),
                        item.expiresAt(),
                        item.enabled(),
                        item.stackable(),
                        item.description()
                ))
                .toList();
    }

    @PostMapping("/redeem-codes")
    @Operation(summary = "创建兑换码", description = "创建本地项目兑换码（支持个人码/批次码）。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminRedeemCodeDto createRedeemCode(@Valid @RequestBody AdminRedeemCodeCreateRequest request) {
        EconomyService.RedeemCodeView item = economyService.createRedeemCode(
                request.code(),
                request.grantAmount(),
                request.maxUses(),
                request.startsAt(),
                request.expiresAt(),
                request.enabled() == null || request.enabled(),
                request.stackable() == null || request.stackable(),
                request.description()
        );
        return new AdminRedeemCodeDto(
                item.id(),
                item.code(),
                item.grantAmount(),
                item.maxUses(),
                item.usedCount(),
                item.startsAt(),
                item.expiresAt(),
                item.enabled(),
                item.stackable(),
                item.description()
        );
    }

    @GetMapping("/credits/conversions")
    @Operation(summary = "查询通用积分兑换订单", description = "分页查询全站通用积分兑换为项目积分的订单记录。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public List<AdminConversionOrderDto> conversionOrders(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return economyService.listConversions(PageRequest.of(safePage, safeSize))
                .stream()
                .map(item -> new AdminConversionOrderDto(
                        item.id(),
                        item.orderNo(),
                        item.userId(),
                        item.username(),
                        item.requestedAmount(),
                        item.convertedAmount(),
                        item.projectBefore(),
                        item.projectAfter(),
                        item.publicBefore(),
                        item.publicAfter(),
                        item.status(),
                        item.remoteMessage(),
                        item.createdAt()
                ))
                .toList();
    }

    @GetMapping("/credits/ledger")
    @Operation(summary = "查询项目积分流水", description = "分页查询全站项目积分流水。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public List<AdminCreditLedgerItemDto> ledger(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
    ) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        return economyService.listLedger(PageRequest.of(safePage, safeSize))
                .stream()
                .map(item -> new AdminCreditLedgerItemDto(
                        item.id(),
                        item.userId(),
                        item.username(),
                        item.type(),
                        item.delta(),
                        item.balanceAfter(),
                        item.referenceType(),
                        item.referenceId(),
                        item.description(),
                        item.createdAt()
                ))
                .toList();
    }

    private User resolveTargetUser(String userId) {
        String value = userId == null ? "" : userId.trim();
        if (value.isBlank()) {
            throw new RuntimeException("目标用户不能为空");
        }
        try {
            return userRepository.findById(UUID.fromString(value)).orElseThrow(() -> new RuntimeException("用户不存在"));
        } catch (IllegalArgumentException ignore) {
            try {
                long remoteUid = Long.parseLong(value);
                return userRepository.findByRemoteUid(remoteUid).orElseThrow(() -> new RuntimeException("用户不存在"));
            } catch (NumberFormatException ex) {
                throw new RuntimeException("用户 ID 格式错误");
            }
        }
    }

    private AdminUserDto toUserDto(UserAdminRemoteClient.RemoteAdminUser remote) {
        User local = userRepository.findByRemoteUid(remote.id()).orElse(null);
        double credits = local != null ? local.getCredits() : 0.0;
        return new AdminUserDto(
                String.valueOf(remote.id()),
                remote.username(),
                remote.email(),
                "ADMIN".equalsIgnoreCase(remote.role()) ? "admin" : "user",
                credits,
                remote.banned(),
                local == null ? null : local.getLastCheckInAt()
        );
    }
}
