package com.ainovel.app.adminauth;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class AdminLocalAuthService {
    static final String SUBJECT = "configured-admin";
    static final int CHALLENGE_TTL_SECONDS = 120;
    private static final int MAX_CHALLENGE_ATTEMPTS = 5;

    private final AdminLocalAuthProperties properties;
    private final AdminAuthPolicySource policy;
    private final AdminAuthStore store;
    private final AdminAuthCrypto crypto;
    private final AdminSessionService sessions;
    private final AdminRateLimiter limiter;
    private final PasswordEncoder passwordEncoder;

    public AdminLocalAuthService(
            AdminLocalAuthProperties properties,
            AdminAuthPolicySource policy,
            AdminAuthStore store,
            AdminAuthCrypto crypto,
            AdminSessionService sessions,
            AdminRateLimiter limiter,
            PasswordEncoder passwordEncoder
    ) {
        this.properties = properties;
        this.policy = policy;
        this.store = store;
        this.crypto = crypto;
        this.sessions = sessions;
        this.limiter = limiter;
        this.passwordEncoder = passwordEncoder;
    }

    public Bootstrap bootstrap(String source) {
        allowOrReject("bootstrap", properties.getUsername(), source, 20, Duration.ofMinutes(5));
        String state;
        if (policy.isPasswordMode()) {
            state = "PASSWORD_REQUIRED";
        } else {
            state = store.credentialExists(SUBJECT) ? "TOTP_REQUIRED" : "ENROLLMENT_REQUIRED";
        }
        return new Bootstrap(policy.env(), policy.authMode(), state, CHALLENGE_TTL_SECONDS, MAX_CHALLENGE_ATTEMPTS);
    }

    public boolean secureCookie() {
        return properties.isCookieSecure();
    }

    @Transactional
    public LoginStart login(String username, String password, String source) {
        String account = username == null ? "" : username;
        allowOrReject("login-password", account, source, 8, Duration.ofMinutes(5));
        boolean passwordMatches = passwordEncoder.matches(
                password == null ? "" : password,
                properties.getPasswordHash()
        );
        if (!secureEquals(account, properties.getUsername()) || !passwordMatches) {
            store.audit("admin.login.password", SUBJECT, null, source, "FAILED", "AUTH_FAILURE");
            throw invalid();
        }

        Instant passwordAt = Instant.now();
        if (policy.isPasswordMode()) {
            AdminSessionService.Issued session = sessions.issue(
                    SUBJECT, "FULL", "PASSWORD", passwordAt, null, null
            );
            store.audit("admin.login.password", SUBJECT, null, source, "SUCCESS", null);
            return LoginStart.authenticated(properties.getUsername(), session);
        }

        boolean enrolled = store.credentialExists(SUBJECT);
        String purpose = enrolled ? "LOGIN" : "ENROLLMENT_PASSWORD";
        String challenge = createChallenge(purpose, null, passwordAt);
        store.audit("admin.login.password", SUBJECT, hash(challenge), source, "SUCCESS",
                enrolled ? "TOTP_REQUIRED" : "ENROLLMENT_REQUIRED");
        return LoginStart.challenge(
                enrolled ? "TOTP_REQUIRED" : "ENROLLMENT_REQUIRED",
                properties.getUsername(),
                challenge,
                Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS)
        );
    }

    @Transactional
    public EnrollmentStart startEnrollment(String passwordChallenge, String source) {
        requireTotpMode();
        allowOrReject("enrollment-start", properties.getUsername(), source, 5, Duration.ofMinutes(10));
        ChallengeData gate = challenge(passwordChallenge, "ENROLLMENT_PASSWORD");
        if (store.credentialExists(SUBJECT) || !store.consumeChallenge(gate.hash())) {
            throw invalid();
        }
        String secret = crypto.randomBase32(20);
        String challenge = createChallenge("ENROLLMENT", secret, gate.passwordAuthenticatedAt());
        String issuer = "AINovel Admin";
        String label = properties.getUsername() + "@AINovel";
        String uri = "otpauth://totp/" + enc(issuer + ":" + label) + "?secret=" + secret
                + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
        store.audit("admin.enrollment.start", SUBJECT, hash(challenge), source, "SUCCESS", null);
        return new EnrollmentStart(challenge, uri, secret, Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS));
    }

    @Transactional
    public LoginResult confirmEnrollment(String challenge, String code, String source) {
        requireTotpMode();
        allowOrReject("enrollment-confirm", properties.getUsername(), source, 10, Duration.ofMinutes(5));
        ChallengeData data = challenge(challenge, "ENROLLMENT");
        long timestep = verifyChallengeSecret(data, code);
        if (!store.consumeChallenge(data.hash()) || store.credentialExists(SUBJECT)) {
            throw invalid();
        }
        AdminAuthCrypto.EncryptedValue encrypted = crypto.encrypt(data.secret(), properties.getActiveKeyVersion());
        store.insertCredential(SUBJECT, encrypted, Instant.now());
        if (!store.acceptTimestep(SUBJECT, timestep)) {
            throw invalid();
        }
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        AdminSessionService.Issued session = sessions.issue(
                SUBJECT, "FULL", "PASSWORD_TOTP", data.passwordAuthenticatedAt(), Instant.now(),
                encrypted.keyVersion()
        );
        store.audit("admin.enrollment.confirm", SUBJECT, data.hash(), source, "SUCCESS", null);
        return LoginResult.from(properties.getUsername(), session, recovery);
    }

    @Transactional
    public LoginResult loginTotp(String challenge, String code, String source) {
        requireTotpMode();
        allowOrReject("login-totp", properties.getUsername(), source, 10, Duration.ofMinutes(5));
        ChallengeData data = challenge(challenge, "LOGIN");
        if (!store.incrementAttempt(data.hash())) {
            throw invalid();
        }
        AdminAuthStore.Credential credential = store.credential(SUBJECT).orElseThrow(this::invalid);
        String secret = crypto.decrypt(credential.encryptedSecret(), credential.nonce(), credential.keyVersion());
        long timestep = matchingTimestep(secret, code, credential);
        if (timestep < 0 || !store.acceptTimestep(SUBJECT, timestep) || !store.consumeChallenge(data.hash())) {
            throw invalid();
        }
        rotateCredentialKeyIfNeeded(credential, secret);
        AdminSessionService.Issued session = sessions.issue(
                SUBJECT, "FULL", "PASSWORD_TOTP", data.passwordAuthenticatedAt(), Instant.now(),
                properties.getActiveKeyVersion()
        );
        store.audit("admin.login.totp", SUBJECT, data.hash(), source, "SUCCESS", null);
        return LoginResult.from(properties.getUsername(), session, List.of());
    }

    @Transactional
    public LoginResult loginRecovery(String challenge, String recoveryCode, String source) {
        requireTotpMode();
        allowOrReject("login-recovery", properties.getUsername(), source, 6, Duration.ofMinutes(10));
        ChallengeData data = challenge(challenge, "LOGIN");
        if (!store.incrementAttempt(data.hash())) {
            throw invalid();
        }
        String normalized = normalizeRecovery(recoveryCode);
        String matching = store.activeRecoveryHashes(SUBJECT).stream()
                .filter(hash -> crypto.matchesRecoveryCode(normalized, hash))
                .findFirst()
                .orElseThrow(this::invalid);
        if (!store.consumeRecoveryHash(SUBJECT, matching) || !store.consumeChallenge(data.hash())) {
            throw invalid();
        }
        sessions.revokeAll(SUBJECT);
        AdminSessionService.Issued session = sessions.issue(
                SUBJECT, "RECOVERY", "PASSWORD_RECOVERY", data.passwordAuthenticatedAt(), null,
                store.credential(SUBJECT).map(AdminAuthStore.Credential::keyVersion).orElse(null)
        );
        store.audit("admin.login.recovery", SUBJECT, data.hash(), source, "SUCCESS", "RESTRICTED_SESSION");
        return LoginResult.from(properties.getUsername(), session, List.of());
    }

    public RebindStart startRebind(String sessionHash, String source) {
        requireTotpMode();
        if (!sessions.isActive(sessionHash, "RECOVERY")) {
            throw invalid();
        }
        allowOrReject("rebind-start", properties.getUsername(), source, 3, Duration.ofMinutes(5));
        String secret = crypto.randomBase32(20);
        String challenge = createChallenge("RESET", secret, Instant.now());
        String uri = "otpauth://totp/" + enc("AINovel Admin:" + properties.getUsername()) + "?secret=" + secret
                + "&issuer=AINovel%20Admin&algorithm=SHA1&digits=6&period=30";
        return new RebindStart(challenge, uri, secret, Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS));
    }

    @Transactional
    public LoginResult confirmRebind(String sessionHash, String challenge, String code, String source) {
        requireTotpMode();
        if (!sessions.isActive(sessionHash, "RECOVERY")) {
            throw invalid();
        }
        ChallengeData data = challenge(challenge, "RESET");
        long timestep = verifyChallengeSecret(data, code);
        if (!store.consumeChallenge(data.hash())) {
            throw invalid();
        }
        AdminAuthCrypto.EncryptedValue encrypted = crypto.encrypt(data.secret(), properties.getActiveKeyVersion());
        if (store.replaceCredential(SUBJECT, encrypted, Instant.now()) != 1
                || !store.acceptTimestep(SUBJECT, timestep)) {
            throw invalid();
        }
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        sessions.revokeAll(SUBJECT);
        AdminSessionService.Issued session = sessions.issue(
                SUBJECT, "FULL", "PASSWORD_TOTP", data.passwordAuthenticatedAt(), Instant.now(),
                encrypted.keyVersion()
        );
        store.audit("admin.rebind.confirm", SUBJECT, data.hash(), source, "SUCCESS", null);
        return LoginResult.from(properties.getUsername(), session, recovery);
    }

    public MeResult me(String scope) {
        String assurance = "RECOVERY".equals(scope)
                ? "PASSWORD_RECOVERY"
                : policy.isPasswordMode() ? "PASSWORD" : "PASSWORD_TOTP";
        return new MeResult(properties.getUsername(), scope, assurance, policy.env(), policy.authMode(),
                policy.requiresTotp() ? store.activeRecoveryCount(SUBJECT) : 0);
    }

    public void logout(String sessionHash) {
        sessions.revokeByHash(sessionHash);
    }

    public SecurityStatus status() {
        return new SecurityStatus(
                policy.requiresTotp() && store.credentialExists(SUBJECT),
                policy.requiresTotp() ? store.activeRecoveryCount(SUBJECT) : 0,
                3,
                policy.authMode()
        );
    }

    @Transactional
    public List<String> regenerateRecovery(String sessionHash, String code, String source) {
        requireTotpMode();
        if (!sessions.isActive(sessionHash, "FULL")) {
            throw invalid();
        }
        allowOrReject("recovery-regenerate", properties.getUsername(), source, 5, Duration.ofMinutes(10));
        if (!verifyCurrentTotp(code)) {
            throw invalid();
        }
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        store.audit("admin.recovery.regenerate", SUBJECT, null, source, "SUCCESS", null);
        return recovery;
    }

    @Transactional
    public boolean verifyCurrentTotp(String code) {
        if (!policy.requiresTotp()) {
            return false;
        }
        AdminAuthStore.Credential credential = store.credential(SUBJECT).orElse(null);
        if (credential == null) {
            return false;
        }
        String secret = crypto.decrypt(credential.encryptedSecret(), credential.nonce(), credential.keyVersion());
        long timestep = matchingTimestep(secret, code, credential);
        if (timestep < 0 || !store.acceptTimestep(SUBJECT, timestep)) {
            return false;
        }
        rotateCredentialKeyIfNeeded(credential, secret);
        return true;
    }

    public void recordFailure(String source, String reason) {
        store.audit("admin.authentication.failed", SUBJECT, null, source, "FAILED", reason);
    }

    @Transactional
    public void resetFromApprovedOperation(String approvalId) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("A reset approval identifier is required");
        }
        Instant now = Instant.now();
        store.clearAuthentication(SUBJECT, now);
        store.audit("admin.reset.execute", SUBJECT, null, "operator-command", "SUCCESS", approvalId.trim());
    }

    private long verifyChallengeSecret(ChallengeData data, String code) {
        if (!store.incrementAttempt(data.hash()) || data.secret() == null) {
            throw invalid();
        }
        long timestep = TotpService.matchingTimestep(
                data.secret(), normalizeCode(code), Instant.now().getEpochSecond(), -1, 6, 30
        );
        if (timestep < 0) {
            throw invalid();
        }
        return timestep;
    }

    private long matchingTimestep(String secret, String code, AdminAuthStore.Credential credential) {
        return TotpService.matchingTimestep(
                secret,
                normalizeCode(code),
                Instant.now().getEpochSecond(),
                credential.lastAcceptedTimestep() == null ? -1 : credential.lastAcceptedTimestep(),
                credential.digits(),
                credential.periodSeconds()
        );
    }

    private void rotateCredentialKeyIfNeeded(AdminAuthStore.Credential credential, String secret) {
        if (!properties.getActiveKeyVersion().equals(credential.keyVersion())) {
            store.rotateCredentialKey(SUBJECT, crypto.encrypt(secret, properties.getActiveKeyVersion()));
        }
    }

    private ChallengeData challenge(String raw, String purpose) {
        if (raw == null || raw.isBlank()) {
            throw invalid();
        }
        return store.challenge(hash(raw))
                .filter(value -> purpose.equals(value.purpose()))
                .filter(value -> value.consumedAt() == null)
                .filter(value -> value.expiresAt().isAfter(Instant.now()))
                .map(value -> new ChallengeData(
                        value.hash(),
                        value.encryptedSecret() == null ? null : crypto.decrypt(
                                value.encryptedSecret(), value.nonce(), value.keyVersion()
                        ),
                        value.passwordAuthenticatedAt()
                ))
                .orElseThrow(this::invalid);
    }

    private String createChallenge(String purpose, String secret, Instant passwordAuthenticatedAt) {
        String raw = crypto.randomBase32(32);
        AdminAuthCrypto.EncryptedValue encrypted = secret == null
                ? null
                : crypto.encrypt(secret, properties.getActiveKeyVersion());
        store.insertChallenge(
                hash(raw), SUBJECT, purpose, encrypted, passwordAuthenticatedAt,
                Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS)
        );
        return raw;
    }

    private void allowOrReject(
            String action,
            String account,
            String source,
            int limit,
            Duration window
    ) {
        String normalizedAccount = account == null || account.isBlank() ? "unknown" : hash(account);
        String normalizedSource = source == null || source.isBlank() ? "unknown" : source;
        boolean allowed = limiter.allow(action + ":account:" + normalizedAccount, limit, window)
                && limiter.allow(action + ":ip:" + normalizedSource, limit * 2, window)
                && limiter.allow(action + ":combination:" + normalizedAccount + ":" + normalizedSource, limit, window)
                && limiter.allow(action + ":global", limit * 10, window);
        if (!allowed) {
            throw new AdminAuthenticationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "认证请求过于频繁，请稍后重试",
                    Math.toIntExact(Math.max(1, window.toSeconds()))
            );
        }
    }

    private void requireTotpMode() {
        if (!policy.requiresTotp()) {
            throw new AdminAuthenticationException(
                    HttpStatus.NOT_FOUND, "TOTP_DISABLED", "当前认证模式未启用动态验证码"
            );
        }
    }

    private List<String> generateRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            codes.add(crypto.randomCode());
        }
        return codes;
    }

    private boolean secureEquals(String left, String right) {
        byte[] a = (left == null ? "" : left).getBytes(StandardCharsets.UTF_8);
        byte[] b = (right == null ? "" : right).getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    private String normalizeCode(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String normalizeRecovery(String value) {
        return normalizeCode(value).replace("-", "").toUpperCase(Locale.ROOT);
    }

    static String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private AdminAuthenticationException invalid() {
        return new AdminAuthenticationException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "认证失败");
    }

    private record ChallengeData(String hash, String secret, Instant passwordAuthenticatedAt) {}

    public record Bootstrap(
            String env,
            String authMode,
            String state,
            int challengeTtlSeconds,
            int maxChallengeAttempts
    ) {}

    public record LoginStart(
            String status,
            String username,
            String challengeId,
            Instant expiresAt,
            AdminSessionService.Issued session
    ) {
        static LoginStart authenticated(String username, AdminSessionService.Issued session) {
            return new LoginStart("AUTHENTICATED", username, null, session.expiresAt(), session);
        }

        static LoginStart challenge(String status, String username, String challengeId, Instant expiresAt) {
            return new LoginStart(status, username, challengeId, expiresAt, null);
        }
    }

    public record EnrollmentStart(String challengeId, String otpauthUri, String manualKey, Instant expiresAt) {}
    public record RebindStart(String challengeId, String otpauthUri, String manualKey, Instant expiresAt) {}

    public record LoginResult(
            String token,
            String username,
            String sessionScope,
            String assurance,
            Instant expiresAt,
            List<String> recoveryCodes
    ) {
        static LoginResult from(
                String username,
                AdminSessionService.Issued session,
                List<String> recoveryCodes
        ) {
            return new LoginResult(
                    session.token(), username, session.scope(), session.assurance(), session.expiresAt(), recoveryCodes
            );
        }
    }

    public record MeResult(
            String username,
            String sessionScope,
            String assurance,
            String env,
            String authMode,
            int recoveryCodesRemaining
    ) {}

    public record SecurityStatus(
            boolean enrolled,
            int recoveryCodesRemaining,
            int lowRecoveryThreshold,
            String authMode
    ) {}
}
