package com.ainovel.app.adminauth;

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

    public AdminAuthController(AdminLocalAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "管理员登录", description = "使用 env.txt 的 ADMIN_USERNAME / ADMIN_PASSWORD 获取本地管理员 token。")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            AdminLocalAuthService.LoginResult result = authService.login(request.username(), request.password());
            return ResponseEntity.ok(Map.of(
                    "token", result.token(),
                    "username", result.username(),
                    "loggedInAt", result.loggedInAt()
            ));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", ex.getMessage()));
        } catch (RuntimeException ex) {
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
        return ResponseEntity.ok(Map.of("success", true));
    }

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {
    }
}

