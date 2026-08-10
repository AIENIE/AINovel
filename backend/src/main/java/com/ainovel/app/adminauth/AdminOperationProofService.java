package com.ainovel.app.adminauth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class AdminOperationProofService {
    public static final int CHALLENGE_TTL_SECONDS = 120;
    public static final int PROOF_TTL_SECONDS = 60;
    private static final String AUDIT_CHALLENGE_CREATED = "CHALLENGE_CREATED";
    private static final String AUDIT_PROOF_ISSUED = "PROOF_ISSUED";

    private final AdminAuthPolicySource policy;
    private final AdminAuthStore store;
    private final AdminLocalAuthService authService;
    private final AdminRateLimiter limiter;
    private final SecureRandom random = new SecureRandom();

    public AdminOperationProofService(
            AdminAuthPolicySource policy,
            AdminAuthStore store,
            AdminLocalAuthService authService,
            AdminRateLimiter limiter
    ) {
        this.policy = policy;
        this.store = store;
        this.authService = authService;
        this.limiter = limiter;
    }

    public OperationChallenge createChallenge(
            String subject,
            String sessionHash,
            String actionKey,
            String targetId,
            String source
    ) {
        requireTotpMode();
        allow("proof-mint", subject, sessionHash, source, 5, Duration.ofMinutes(1));
        String raw = newToken();
        String challengeHash = AdminLocalAuthService.hash(raw);
        Instant expiresAt = Instant.now().plusSeconds(CHALLENGE_TTL_SECONDS);
        store.insertOperationChallenge(
                challengeHash, subject, sessionHash, actionKey, targetId, source, expiresAt
        );
        store.audit("admin.operation-proof.challenge", subject, challengeHash, source, "SUCCESS",
                AUDIT_CHALLENGE_CREATED);
        return new OperationChallenge(raw, expiresAt);
    }

    @Transactional
    public ProofVerification verifyAndIssue(
            String challengeId,
            String sessionHash,
            String code,
            String source
    ) {
        requireTotpMode();
        String challengeHash = challengeId == null || challengeId.isBlank()
                ? ""
                : AdminLocalAuthService.hash(challengeId);
        AdminAuthStore.OperationChallenge challenge = store.operationChallenge(challengeHash).orElse(null);
        String subject = challenge == null ? AdminLocalAuthService.SUBJECT : challenge.subject();
        allow("proof-verify", subject, sessionHash, source, 10, Duration.ofMinutes(1));
        if (challenge == null
                || challenge.consumedAt() != null
                || !challenge.expiresAt().isAfter(Instant.now())
                || !sessionHash.equals(challenge.sessionHash())
                || !store.incrementOperationAttempt(challengeHash)
                || !authService.verifyCurrentTotp(code)
                || !store.consumeOperationChallenge(challengeHash)) {
            store.audit("admin.operation-proof.verify", subject,
                    challengeHash.isBlank() ? null : challengeHash, source, "FAILED", "INVALID_OR_EXPIRED");
            throw new AdminAuthenticationException(
                    HttpStatus.UNAUTHORIZED,
                    "OPERATION_PROOF_INVALID",
                    "二次验证失败或已过期，请重新发起操作"
            );
        }
        String rawProof = newToken();
        store.insertOperationProof(
                AdminLocalAuthService.hash(rawProof),
                challenge.subject(),
                challenge.sessionHash(),
                challenge.actionKey(),
                challenge.targetId(),
                Instant.now().plusSeconds(PROOF_TTL_SECONDS)
        );
        store.audit("admin.operation-proof.verify", subject, challenge.hash(), source, "SUCCESS", AUDIT_PROOF_ISSUED);
        return new ProofVerification(rawProof, PROOF_TTL_SECONDS);
    }

    @Transactional
    public boolean consume(
            String proofToken,
            String subject,
            String sessionHash,
            String actionKey,
            String targetId
    ) {
        if (proofToken == null || proofToken.isBlank()) {
            return false;
        }
        return store.consumeOperationProof(
                AdminLocalAuthService.hash(proofToken), subject, sessionHash, actionKey, targetId
        );
    }

    private void allow(
            String action,
            String subject,
            String sessionHash,
            String source,
            int limit,
            Duration window
    ) {
        String user = subject == null || subject.isBlank() ? "unknown" : subject;
        String session = sessionHash == null || sessionHash.isBlank() ? "unknown" : sessionHash;
        String ip = source == null || source.isBlank() ? "unknown" : source;
        if (!limiter.allow(action + ":user:" + user, limit, window)
                || !limiter.allow(action + ":session:" + session, limit, window)
                || !limiter.allow(action + ":ip:" + ip, limit * 2, window)
                || !limiter.allow(action + ":combination:" + user + ":" + session + ":" + ip, limit, window)) {
            throw new AdminAuthenticationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMITED",
                    "二次验证请求过于频繁，请稍后重试",
                    Math.toIntExact(window.toSeconds())
            );
        }
    }

    private void requireTotpMode() {
        if (!policy.requiresTotp()) {
            throw new AdminAuthenticationException(
                    HttpStatus.NOT_FOUND, "TOTP_DISABLED", "当前认证模式不需要二次验证"
            );
        }
    }

    private String newToken() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    public record OperationChallenge(String challengeId, Instant expiresAt) {}
    public record ProofVerification(String proofToken, int expiresInSeconds) {}
}
