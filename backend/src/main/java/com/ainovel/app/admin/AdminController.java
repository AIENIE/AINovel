package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.*;
import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.User;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "管理端接口（聚合第三方服务 + 本地管理能力）")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {
    private final AdminConsoleService adminConsoleService;
    private final EconomyService economyService;
    private final OpsRecordFileSink recordFileSink;

    public AdminController(
            AdminConsoleService adminConsoleService,
            EconomyService economyService,
            OpsRecordFileSink recordFileSink
    ) {
        this.adminConsoleService = adminConsoleService;
        this.economyService = economyService;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "获取管理看板", description = "返回用户规模、今日新增、待审核素材、消费与错误率等汇总指标。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public ResponseEntity<AdminDashboardStatsResponse> dashboard() {
        return ResponseEntity.ok(adminConsoleService.dashboard());
    }

    @GetMapping("/users")
    @Operation(summary = "查询项目用户运营列表", description = "返回 AINovel 本地用户镜像、专属积分和创作资产统计；不管理 UserService 全局账号能力。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public List<AdminUserDto> users(
            @Parameter(description = "用户名/邮箱关键字", example = "demo")
            @RequestParam(value = "search", required = false) String search
    ) {
        return adminConsoleService.users(search);
    }

    @GetMapping("/system-config")
    @Operation(summary = "读取系统配置", description = "返回 AINovel 本地维护模式。SMTP、注册、签到和 AI 模型池配置由对应公共服务或业务链路负责。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "读取成功"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminSystemConfigResponse systemConfig() {
        return adminConsoleService.systemConfig();
    }

    @PutMapping("/system-config")
    @Operation(summary = "更新系统配置", description = "仅更新 AINovel 本地维护模式。")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "更新成功",
                    content = @Content(
                            schema = @Schema(implementation = AdminSystemConfigResponse.class),
                            examples = @ExampleObject(value = "{\"maintenanceMode\":false}")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminSystemConfigResponse updateSystemConfig(
            @AuthenticationPrincipal UserDetails principal,
            @RequestBody AdminSystemConfigUpdateRequest request
    ) {
        AdminSystemConfigResponse response = adminConsoleService.updateSystemConfig(request.maintenanceMode());
        audit(principal, "maintenance.update", "system-config", "global", "SUCCESS", "INFO");
        return response;
    }

    @PostMapping("/credits/grant")
    @Operation(summary = "管理员加发项目专属积分", description = "写入 AINovel 本地专属积分账户与流水，仅支持加分。")
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
        User target = adminConsoleService.resolveTargetUser(request.userId());
        EconomyService.CreditChangeResult result = economyService.grantProjectCredits(
                target,
                request.amount(),
                request.reason(),
                principal == null ? "admin" : principal.getUsername()
        );
        audit(principal, "credits.grant", "user", String.valueOf(target.getId()), "SUCCESS", "INFO");
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
    @Operation(summary = "兑换码列表", description = "查询 AINovel 本地项目专属积分兑换码。")
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
    @Operation(summary = "创建兑换码", description = "创建 AINovel 本地项目专属积分兑换码。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "创建成功"),
            @ApiResponse(responseCode = "400", description = "参数错误"),
            @ApiResponse(responseCode = "401", description = "未登录"),
            @ApiResponse(responseCode = "403", description = "无管理员权限")
    })
    public AdminRedeemCodeDto createRedeemCode(
            @AuthenticationPrincipal UserDetails principal,
            @Valid @RequestBody AdminRedeemCodeCreateRequest request
    ) {
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
        audit(principal, "redeem-code.create", "redeem-code", item.code(), "SUCCESS", "INFO");
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
    @Operation(summary = "查询项目专属积分流水", description = "分页查询 AINovel 本地项目专属积分流水。")
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

    private void audit(UserDetails principal, String action, String targetType, String targetId, String result, String severity) {
        recordFileSink.appendAudit(Map.of(
                "category", "admin",
                "action", action,
                "actor", principal == null ? "admin" : principal.getUsername(),
                "targetType", targetType,
                "targetId", targetId,
                "result", result,
                "severity", severity
        ));
    }
}
