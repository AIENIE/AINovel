package com.ainovel.app.adminauth;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/admin-auth")
public class AdminAuthController {
    private final AdminLocalAuthService authService;
    private final AdminOperationProofService operationProofs;
    private final OpsRecordFileSink recordFileSink;

    public AdminAuthController(
            AdminLocalAuthService authService,
            AdminOperationProofService operationProofs,
            OpsRecordFileSink recordFileSink
    ) {
        this.authService = authService;
        this.operationProofs = operationProofs;
        this.recordFileSink = recordFileSink;
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<?> bootstrap(HttpServletRequest request) {
        return noStore(authService.bootstrap(source(request)));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        AdminLocalAuthService.LoginStart result = authService.login(
                body.username(), body.password(), source(request)
        );
        if (result.session() != null) {
            return authenticated(new AdminLocalAuthService.LoginResult(
                    result.session().token(),
                    result.username(),
                    result.session().scope(),
                    result.session().assurance(),
                    result.session().expiresAt(),
                    List.of()
            ));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(new LoginChallengeResponse(
                        result.status(), result.username(), result.challengeId(), result.expiresAt()
                ));
    }

    @PostMapping("/login/challenge")
    public ResponseEntity<?> disabledPasswordlessLogin() {
        return ResponseEntity.status(HttpStatus.GONE)
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "code", "PASSWORDLESS_LOGIN_DISABLED",
                        "message", "请先提交管理员账号和密码"
                ));
    }

    @PostMapping("/enrollment/start")
    public ResponseEntity<?> startEnrollment(
            @Valid @RequestBody ChallengeRequest body,
            HttpServletRequest request
    ) {
        return noStore(authService.startEnrollment(body.challengeId(), source(request)));
    }

    @PostMapping("/enrollment/confirm")
    public ResponseEntity<?> confirmEnrollment(
            @Valid @RequestBody CodeRequest body,
            HttpServletRequest request
    ) {
        return authenticated(authService.confirmEnrollment(body.challengeId(), body.code(), source(request)));
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
        return authenticated(authService.loginRecovery(
                body.challengeId(), body.recoveryCode(), source(request)
        ));
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyAuthority('AUTH_LOCAL_ADMIN','AUTH_LOCAL_ADMIN_RECOVERY')")
    public ResponseEntity<?> me(Authentication authentication) {
        return noStore(authService.me(scope(authentication)));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAnyAuthority('AUTH_LOCAL_ADMIN','AUTH_LOCAL_ADMIN_RECOVERY')")
    public ResponseEntity<?> logout(Authentication authentication) {
        authService.logout(sessionHash(authentication));
        audit("admin.logout", "SUCCESS", "INFO");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredSessionCookie().toString())
                .body(Map.of("success", true));
    }

    @GetMapping("/security/status")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    public ResponseEntity<?> status() {
        return noStore(authService.status());
    }

    @PostMapping("/security/recovery-codes/regenerate")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    public ResponseEntity<?> regenerate(
            @Valid @RequestBody TotpRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return noStore(authService.regenerateRecovery(
                sessionHash(authentication), body.code(), source(request)
        ));
    }

    @PostMapping("/rebind/start")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN_RECOVERY')")
    public ResponseEntity<?> rebindStart(Authentication authentication, HttpServletRequest request) {
        return noStore(authService.startRebind(sessionHash(authentication), source(request)));
    }

    @PostMapping("/rebind/confirm")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN_RECOVERY')")
    public ResponseEntity<?> rebindConfirm(
            @Valid @RequestBody CodeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return authenticated(authService.confirmRebind(
                sessionHash(authentication), body.challengeId(), body.code(), source(request)
        ));
    }

    @PostMapping("/operation-proofs/verify")
    @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    public ResponseEntity<?> verifyOperationProof(
            @Valid @RequestBody CodeRequest body,
            Authentication authentication,
            HttpServletRequest request
    ) {
        return noStore(operationProofs.verifyAndIssue(
                body.challengeId(), sessionHash(authentication), body.code(), source(request)
        ));
    }

    @ExceptionHandler(AdminAuthenticationException.class)
    public ResponseEntity<?> authenticationFailure(
            AdminAuthenticationException exception,
            HttpServletRequest request
    ) {
        authService.recordFailure(source(request), exception.code());
        audit("admin.authentication.failure", "FAILED", "WARN");
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(exception.status())
                .cacheControl(CacheControl.noStore());
        if (exception.retryAfterSeconds() > 0) {
            builder.header("Retry-After", Integer.toString(exception.retryAfterSeconds()));
        }
        return builder.body(Map.of("code", exception.code(), "message", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalidRequest(HttpServletRequest request) {
        authService.recordFailure(source(request), "INVALID_REQUEST");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .body(Map.of("code", "AUTHENTICATION_FAILED", "message", "认证失败"));
    }

    private String sessionHash(Authentication authentication) {
        return authentication != null && authentication.getDetails() instanceof String value ? value : "";
    }

    private String scope(Authentication authentication) {
        if (authentication == null) {
            return "";
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> AdminAuthConstants.RECOVERY_AUTHORITY.equals(authority.getAuthority()))
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
        ResponseCookie cookie = ResponseCookie.from(AdminAuthConstants.ADMIN_SESSION_COOKIE, result.token())
                .httpOnly(true)
                .secure(authService.secureCookie())
                .sameSite("Strict")
                .path("/")
                .maxAge(lifetime.isNegative() ? Duration.ZERO : lifetime)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(new AuthenticatedSession(
                        result.username(), result.sessionScope(), result.assurance(),
                        result.expiresAt(), result.recoveryCodes()
                ));
    }

    private ResponseCookie expiredSessionCookie() {
        return ResponseCookie.from(AdminAuthConstants.ADMIN_SESSION_COOKIE, "")
                .httpOnly(true)
                .secure(authService.secureCookie())
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

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    public record ChallengeRequest(@NotBlank String challengeId) {}
    public record CodeRequest(@NotBlank String challengeId, @NotBlank String code) {}
    public record RecoveryRequest(@NotBlank String challengeId, @NotBlank String recoveryCode) {}
    public record TotpRequest(@NotBlank String code) {}
    public record LoginChallengeResponse(String status, String username, String challengeId, java.time.Instant expiresAt) {}
    public record AuthenticatedSession(
            String username,
            String sessionScope,
            String assurance,
            java.time.Instant expiresAt,
            List<String> recoveryCodes
    ) {}
}
