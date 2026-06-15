package com.ainovel.app.adminauth;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/v1/admin-auth")
@Tag(name = "AdminAuth", description = "管理员本地账号密码登录")
public class AdminAuthController {
    private final AdminLocalAuthService authService;
    private final OpsRecordFileSink recordFileSink;

    public AdminAuthController(AdminLocalAuthService authService, OpsRecordFileSink recordFileSink) {
        this.authService = authService;
        this.recordFileSink = recordFileSink;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "使用 env.txt 的 ADMIN_USERNAME / ADMIN_PASSWORD 获取本地管理员 token。")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String actor = request == null ? "" : request.username();
        String password = request == null ? "" : request.password();
        try {
            AdminLocalAuthService.LoginResult result = authService.login(actor, password);
            audit("admin.login", actor, "SUCCESS", "INFO");
            return ResponseEntity.ok(Map.of(
                    "token", result.token(),
                    "username", result.username(),
                    "loggedInAt", result.loggedInAt()
            ));
        } catch (IllegalStateException ex) {
            audit("admin.login", actor, "FAILED", "ERROR");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
            audit("admin.login", actor, "FAILED", "WARN");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "当前管理员信息", description = "返回当前管理员用户名。")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserDetails principal) {
        String username = principal == null ? "" : principal.getUsername();
        return ResponseEntity.ok(authService.me(username));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "管理员退出", description = "JWT 无状态退出，前端删除 token 即可。")
    public ResponseEntity<?> logout() {
        audit("admin.logout", "admin", "SUCCESS", "INFO");
        return ResponseEntity.ok(Map.of("success", true));
    }

    private void audit(String action, String actor, String result, String severity) {
        recordFileSink.appendAudit(Map.of(
                "category", "admin",
                "action", action,
                "actor", actor == null ? "" : actor,
                "targetType", "admin-session",
                "result", result,
                "severity", severity
        ));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}
