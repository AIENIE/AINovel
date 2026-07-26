package com.ainovel.app.adminauth;

import com.ainovel.app.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminLocalAuthService {
    private static final String SUBJECT = "configured-admin";
    private final AdminLocalAuthProperties properties;
    private final AdminAuthStore store;
    private final AdminAuthCrypto crypto;
    private final AdminSessionService sessions;
    private final AdminRateLimiter limiter;

    public AdminLocalAuthService(AdminLocalAuthProperties properties, AdminAuthStore store, AdminAuthCrypto crypto, AdminSessionService sessions, AdminRateLimiter limiter) {
        this.properties = properties; this.store = store; this.crypto = crypto; this.sessions = sessions; this.limiter = limiter;
    }

    public Bootstrap bootstrap(String source) {
        ensureBaseConfigured();
        allowOrReject("bootstrap", source, 10, 60, Duration.ofMinutes(5));
        boolean enrolled = store.credentialExists(SUBJECT);
        if (!enrolled && (properties.getPassword() == null || properties.getPassword().isBlank())) throw new IllegalStateException("管理员首次绑定配置未完成");
        return new Bootstrap(enrolled ? "TOTP_REQUIRED" : "ENROLLMENT_REQUIRED", 300);
    }

    public EnrollmentStart startEnrollment(String password, String source) {
        ensureBaseConfigured();
        if (properties.getPassword() == null || properties.getPassword().isBlank()) throw new IllegalStateException("管理员首次绑定配置未完成");
        allowOrReject("enrollment", source, 5, 20, Duration.ofMinutes(10));
        if (store.credentialExists(SUBJECT) || !secureEquals(password, properties.getPassword())) throw invalid();
        String secret = crypto.randomBase32(20);
        String challenge = createChallenge("ENROLLMENT", secret);
        String issuer = "AINovel Admin";
        String label = properties.getUsername() + "@AINovel";
        String uri = "otpauth://totp/" + enc(issuer + ":" + label) + "?secret=" + secret + "&issuer=" + enc(issuer) + "&algorithm=SHA1&digits=6&period=30";
        store.audit("admin.enrollment.start", SUBJECT, hash(challenge), source, "SUCCESS", null);
        return new EnrollmentStart(challenge, uri, secret, Instant.now().plusSeconds(300));
    }

    @Transactional
    public LoginResult confirmEnrollment(String challenge, String code, String source) {
        allowOrReject("enrollment-verify", source, 20, 120, Duration.ofMinutes(5));
        ChallengeData data = challenge(challenge, "ENROLLMENT");
        verifyChallengeCode(data, code);
        if (!store.consumeChallenge(data.hash())) throw invalid();
        AdminAuthCrypto.EncryptedValue encrypted = crypto.encrypt(data.secret(), properties.getActiveKeyVersion());
        if (store.credentialExists(SUBJECT)) throw invalid();
        store.insertCredential(SUBJECT, encrypted, Instant.now());
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        AdminSessionService.Issued session = sessions.issue(SUBJECT, "FULL");
        store.audit("admin.enrollment.confirm", SUBJECT, data.hash(), source, "SUCCESS", null);
        return new LoginResult(session.token(), properties.getUsername(), session.scope(), session.expiresAt(), recovery);
    }

    public ChallengeResult createLoginChallenge(String source) {
        allowOrReject("login", source, 10, 60, Duration.ofMinutes(5));
        String challenge = createChallenge("LOGIN", null);
        store.audit("admin.login.challenge", SUBJECT, hash(challenge), source, "SUCCESS", null);
        return new ChallengeResult(challenge, Instant.now().plusSeconds(300), "TOTP_OR_RECOVERY");
    }

    @Transactional
    public LoginResult loginTotp(String challenge, String code, String source) {
        allowOrReject("login-verify", source, 20, 120, Duration.ofMinutes(5));
        ChallengeData data = challenge(challenge, "LOGIN");
        if (!store.incrementAttempt(data.hash())) throw invalid();
        var credential = store.credential(SUBJECT).orElseThrow(this::invalid);
        String secret = crypto.decrypt(credential.encryptedSecret(), credential.nonce(), credential.keyVersion());
        long now = Instant.now().getEpochSecond();
        long timestep = TotpService.matchingTimestep(secret, normalizeCode(code), now, credential.lastAcceptedTimestep() == null ? -1 : credential.lastAcceptedTimestep(), credential.digits(), credential.periodSeconds());
        if (timestep < 0 || !store.acceptTimestep(SUBJECT, timestep) || !store.consumeChallenge(data.hash())) throw invalid();
        rotateCredentialKeyIfNeeded(credential, secret);
        AdminSessionService.Issued session = sessions.issue(SUBJECT, "FULL");
        store.audit("admin.login.totp", SUBJECT, data.hash(), source, "SUCCESS", null);
        return new LoginResult(session.token(), properties.getUsername(), session.scope(), session.expiresAt(), List.of());
    }

    @Transactional
    public LoginResult loginRecovery(String challenge, String recoveryCode, String source) {
        allowOrReject("login-verify", source, 20, 120, Duration.ofMinutes(5));
        ChallengeData data = challenge(challenge, "LOGIN");
        if (!store.incrementAttempt(data.hash())) throw invalid();
        String normalized = normalizeRecovery(recoveryCode);
        String matching = store.activeRecoveryHashes(SUBJECT).stream().filter(hash -> crypto.matchesRecoveryCode(normalized, hash)).findFirst().orElseThrow(this::invalid);
        if (!store.consumeRecoveryHash(SUBJECT, matching) || !store.consumeChallenge(data.hash())) throw invalid();
        AdminSessionService.Issued session = sessions.issue(SUBJECT, "RECOVERY");
        store.audit("admin.login.recovery", SUBJECT, data.hash(), source, "SUCCESS", "RECOVERY_SESSION");
        return new LoginResult(session.token(), properties.getUsername(), session.scope(), session.expiresAt(), List.of());
    }

    @Transactional
    public LoginResult confirmRebind(String sessionId, String challenge, String code, String source) {
        if (!sessions.isActive(sessionId, "RECOVERY")) throw invalid();
        ChallengeData data = challenge(challenge, "RESET");
        verifyChallengeCode(data, code);
        if (!store.consumeChallenge(data.hash())) throw invalid();
        AdminAuthCrypto.EncryptedValue encrypted = crypto.encrypt(data.secret(), properties.getActiveKeyVersion());
        if (store.replaceCredential(SUBJECT, encrypted, Instant.now()) != 1) throw invalid();
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        sessions.revokeAll(SUBJECT);
        AdminSessionService.Issued session = sessions.issue(SUBJECT, "FULL");
        store.audit("admin.rebind.confirm", SUBJECT, data.hash(), source, "SUCCESS", null);
        return new LoginResult(session.token(), properties.getUsername(), session.scope(), session.expiresAt(), recovery);
    }

    public RebindStart startRebind(String sessionId, String source) {
        if (!sessions.isActive(sessionId, "RECOVERY")) throw invalid();
        allowOrReject("rebind", source, 3, 20, Duration.ofMinutes(5));
        String secret = crypto.randomBase32(20);
        String challenge = createChallenge("RESET", secret);
        String uri = "otpauth://totp/" + enc("AINovel Admin:" + properties.getUsername()) + "?secret=" + secret + "&issuer=AINovel%20Admin&algorithm=SHA1&digits=6&period=30";
        return new RebindStart(challenge, uri, secret, Instant.now().plusSeconds(300));
    }

    public MeResult me(String sessionId, String scope) { return new MeResult(properties.getUsername(), scope, store.activeRecoveryCount(SUBJECT)); }
    public void logout(String sessionId) { sessions.revoke(sessionId); }
    public SecurityStatus status() { return new SecurityStatus(store.credentialExists(SUBJECT), store.activeRecoveryCount(SUBJECT), 3); }

    public void recordFailure(String source) {
        store.audit("admin.authentication.failed", SUBJECT, null, source, "FAILED", "AUTH_FAILURE");
    }

    @Transactional
    public List<String> regenerateRecovery(String sessionId, String code, String source) {
        if (!sessions.isActive(sessionId, "FULL")) throw invalid();
        allowOrReject("recovery-regenerate", source, 5, 20, Duration.ofMinutes(10));
        loginTotpAgainstSession(code);
        List<String> recovery = generateRecoveryCodes();
        store.replaceRecoveryCodes(SUBJECT, recovery.stream().map(crypto::hashRecoveryCode).toList(), Instant.now());
        store.audit("admin.recovery.regenerate", SUBJECT, null, source, "SUCCESS", null);
        return recovery;
    }

    private void loginTotpAgainstSession(String code) {
        var credential = store.credential(SUBJECT).orElseThrow(this::invalid);
        String secret = crypto.decrypt(credential.encryptedSecret(), credential.nonce(), credential.keyVersion());
        long step = TotpService.matchingTimestep(secret, normalizeCode(code), Instant.now().getEpochSecond(), credential.lastAcceptedTimestep() == null ? -1 : credential.lastAcceptedTimestep(), credential.digits(), credential.periodSeconds());
        if (step < 0 || !store.acceptTimestep(SUBJECT, step)) throw invalid();
        rotateCredentialKeyIfNeeded(credential, secret);
    }

    private void rotateCredentialKeyIfNeeded(AdminAuthStore.Credential credential, String secret) {
        if (properties.getActiveKeyVersion().equals(credential.keyVersion())) {
            return;
        }
        store.rotateCredentialKey(SUBJECT, crypto.encrypt(secret, properties.getActiveKeyVersion()));
    }

    private ChallengeData challenge(String raw, String purpose) {
        if (raw == null || raw.isBlank()) throw invalid();
        return store.challenge(hash(raw)).filter(c -> purpose.equals(c.purpose()) && c.consumedAt() == null && c.expiresAt().isAfter(Instant.now())).map(c -> new ChallengeData(c.hash(), c.encryptedSecret() == null ? null : crypto.decrypt(c.encryptedSecret(), c.nonce(), c.keyVersion()), c.attempts())).orElseThrow(this::invalid);
    }

    private void verifyChallengeCode(ChallengeData data, String code) {
        if (!store.incrementAttempt(data.hash())) throw invalid();
        if (data.secret() == null || TotpService.matchingTimestep(data.secret(), normalizeCode(code), Instant.now().getEpochSecond(), -1, 6, 30) < 0) throw invalid();
    }

    private String createChallenge(String purpose, String secret) {
        String raw = crypto.randomBase32(32);
        AdminAuthCrypto.EncryptedValue encrypted = secret == null ? null : crypto.encrypt(secret, properties.getActiveKeyVersion());
        store.insertChallenge(hash(raw), SUBJECT, purpose, encrypted, Instant.now().plusSeconds(300));
        return raw;
    }

    private List<String> generateRecoveryCodes() { List<String> codes = new ArrayList<>(); for (int i = 0; i < 8; i++) codes.add(crypto.randomCode()); return codes; }
    private void ensureBaseConfigured() { if (properties.getUsername() == null || properties.getUsername().isBlank()) throw new IllegalStateException("管理员配置未完成"); }
    @Transactional
    public void resetFromApprovedOperation(String approvalId) {
        ensureBaseConfigured();
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("A reset approval identifier is required");
        }
        Instant now = Instant.now();
        store.clearAuthentication(SUBJECT, now);
        store.audit("admin.reset.execute", SUBJECT, null, "operator-command", "SUCCESS", approvalId.trim());
    }

    private void allowOrReject(
            String action,
            String source,
            int sourceLimit,
            int globalLimit,
            Duration window
    ) {
        String sourceKey = action + ":source:" + (source == null || source.isBlank() ? "unknown" : source);
        if (!limiter.allow(sourceKey, sourceLimit, window)
                || !limiter.allow(action + ":global", globalLimit, window)) {
            throw invalid();
        }
    }
    private boolean secureEquals(String a, String b) { byte[] x = (a == null ? "" : a).getBytes(StandardCharsets.UTF_8), y = (b == null ? "" : b).getBytes(StandardCharsets.UTF_8); return java.security.MessageDigest.isEqual(x, y); }
    private String normalizeCode(String value) { return value == null ? "" : value.replaceAll("\\s+", ""); }
    private String normalizeRecovery(String value) { return normalizeCode(value).replace("-", "").toUpperCase(Locale.ROOT); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private BusinessException invalid() { return new BusinessException("认证失败"); }

    private record ChallengeData(String hash, String secret, int attempts) {}
    public record Bootstrap(String state, int challengeTtlSeconds) {}
    public record ChallengeResult(String challengeId, Instant expiresAt, String next) {}
    public record EnrollmentStart(String challengeId, String otpauthUri, String manualKey, Instant expiresAt) {}
    public record RebindStart(String challengeId, String otpauthUri, String manualKey, Instant expiresAt) {}
    public record LoginResult(String token, String username, String sessionScope, Instant expiresAt, List<String> recoveryCodes) {}
    public record MeResult(String username, String sessionScope, int recoveryCodesRemaining) {}
    public record SecurityStatus(boolean enrolled, int recoveryCodesRemaining, int lowRecoveryThreshold) {}
}
