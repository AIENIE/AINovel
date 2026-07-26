package com.ainovel.app.adminauth;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.common.BusinessException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.time.Duration;

@RestController
@RequestMapping("/v1/admin-auth")
@Tag(name = "AdminAuth", description = "管理员 TOTP 登录")
public class AdminAuthController {
    public static final String ADMIN_SESSION_COOKIE = "AINOVEL_ADMIN_SESSION";
    private final AdminLocalAuthService authService;
    private final OpsRecordFileSink recordFileSink;

    public AdminAuthController(AdminLocalAuthService authService, OpsRecordFileSink recordFileSink) {
        this.authService = authService;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(HttpServletRequest request) {
        return noStore(authService.bootstrap(source(request)));
    }

    @PostMapping("/enrollment/start")
    public ResponseEntity<?> startEnrollment(
            @Valid @RequestBody EnrollmentPassword body,
            HttpServletRequest request
    ) {
        return noStore(authService.startEnrollment(body.password(), source(request)));
    }

    @PostMapping("/enrollment/confirm")
    public ResponseEntity<?> confirmEnrollment(
            @Valid @RequestBody CodeRequest body,
            HttpServletRequest request
    ) {
        return authenticated(authService.confirmEnrollment(body.challengeId(), body.code(), source(request)));
    }

    @PostMapping("/login/challenge")
    public ResponseEntity<?> loginChallenge(HttpServletRequest request) {
        return noStore(authService.createLoginChallenge(source(request)));
    }

    @PostMapping("/login/totp")
    public ResponseEntity<?> loginTotp(
            @Valid @RequestBody CodeRequest body,
            HttpServletRequest request
    ) {
        return authenticated(authService.loginTotp(body.challengeId(), body.code(), source(request)));
    }

    @PostMapping("/login/recovery")
    public ResponseEntity<?> loginRecovery(
            @Valid @RequestBody RecoveryRequest body,
            HttpServletRequest request
    ) {
        return authenticated(authService.loginRecovery(body.challengeId(), body.recoveryCode(), source(request)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ADMIN_RECOVERY')")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<?> me(Authentication authentication) {
        return noStore(authService.me(sessionId(authentication), scope(authentication)));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN','ADMIN_RECOVERY')")
    public ResponseEntity<?> logout(Authentication authentication) {
        authService.logout(sessionId(authentication));
        audit("admin.logout", "SUCCESS", "INFO");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
                .body(Map.of("success", true));
    }

    @GetMapping("/security/status")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> status(Authentication authentication) {
        requireFull(authentication);
        return noStore(authService.status());
    }

    @PostMapping("/security/recovery-codes/regenerate")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<?> regenerate(
            @Valid @RequestBody TotpRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireFull(authentication);
        return noStore(authService.regenerateRecovery(sessionId(authentication), body.code(), source(request)));
    }

    @PostMapping("/rebind/start")
    @PreAuthorize("hasAuthority('ADMIN_RECOVERY')")
    public ResponseEntity<?> rebindStart(Authentication authentication, HttpServletRequest request) {
        requireRecovery(authentication);
        return noStore(authService.startRebind(sessionId(authentication), source(request)));
    }

    @PostMapping("/rebind/confirm")
    @PreAuthorize("hasAuthority('ADMIN_RECOVERY')")
    public ResponseEntity<?> rebindConfirm(
            @Valid @RequestBody CodeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        requireRecovery(authentication);
        return authenticated(authService.confirmRebind(sessionId(authentication), body.challengeId(), body.code(), source(request)));
    }

    @ExceptionHandler({BusinessException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<?> authenticationFailure(HttpServletRequest request) {
        authService.recordFailure(source(request));
        audit("admin.authentication.failure", "FAILED", "WARN");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("message", "认证失败"));
    }

    private void requireFull(Authentication authentication) {
        if (!"FULL".equals(scope(authentication))) {
            throw new org.springframework.security.access.AccessDeniedException("恢复会话权限受限");
        }
    }

    private void requireRecovery(Authentication authentication) {
        if (!"RECOVERY".equals(scope(authentication))) {
            throw new org.springframework.security.access.AccessDeniedException("仅恢复会话可重绑");
        }
    }

    private String sessionId(Authentication authentication) {
        return authentication != null && authentication.getDetails() instanceof String value ? value : "";
    }

    private String scope(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ADMIN_RECOVERY"))
                ? "RECOVERY"
                : "FULL";
    }

    private String source(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private ResponseEntity<AuthenticatedSession> authenticated(AdminLocalAuthService.LoginResult result) {
        Duration lifetime = Duration.between(java.time.Instant.now(), result.expiresAt());
        ResponseCookie cookie = ResponseCookie.from(ADMIN_SESSION_COOKIE, result.token())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(lifetime.isNegative() ? Duration.ZERO : lifetime)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthenticatedSession(
                        result.username(),
                        result.sessionScope(),
                        result.expiresAt(),
                        result.recoveryCodes()
                ));
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(ADMIN_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();
    }

    private void audit(String action, String result, String severity) {
        recordFileSink.appendAudit(Map.of(
                "category", "admin-auth",
                "action", action,
                "actor", "configured-admin",
                "result", result,
                "severity", severity
        ));
    }

    public record EnrollmentPassword(@NotBlank String password) {}
    public record CodeRequest(@NotBlank String challengeId, @NotBlank String code) {}
    public record RecoveryRequest(@NotBlank String challengeId, @NotBlank String recoveryCode) {}
    public record TotpRequest(@NotBlank String code) {}
    public record AuthenticatedSession(
            String username,
            String sessionScope,
            java.time.Instant expiresAt,
            java.util.List<String> recoveryCodes
    ) {}
}
