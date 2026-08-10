package com.ainovel.app.adminauth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminAuthStore {
    private final JdbcTemplate jdbc;

    public AdminAuthStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean credentialExists(String subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from admin_totp_credentials where subject_id=?)",
                Boolean.class,
                subject
        ));
    }

    public Optional<Credential> credential(String subject) {
        return jdbc.query(
                "select encrypted_secret,nonce,key_version,algorithm,digits,period_seconds,last_accepted_timestep "
                        + "from admin_totp_credentials where subject_id=?",
                (rs, n) -> new Credential(
                        rs.getString(1), rs.getBytes(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), rs.getInt(6), (Long) rs.getObject(7)
                ),
                subject
        ).stream().findFirst();
    }

    public void insertCredential(String subject, AdminAuthCrypto.EncryptedValue secret, Instant now) {
        jdbc.update(
                "insert into admin_totp_credentials(subject_id,encrypted_secret,nonce,key_version,algorithm,digits,"
                        + "period_seconds,last_accepted_timestep,enabled_at) values(?,?,?,?,?,?,?,?,?)",
                subject, secret.ciphertext(), secret.nonce(), secret.keyVersion(), "HMAC-SHA1", 6, 30, null,
                Timestamp.from(now)
        );
    }

    public int replaceCredential(String subject, AdminAuthCrypto.EncryptedValue secret, Instant now) {
        return jdbc.update(
                "update admin_totp_credentials set encrypted_secret=?,nonce=?,key_version=?,algorithm=?,digits=?,"
                        + "period_seconds=?,last_accepted_timestep=null,enabled_at=? where subject_id=?",
                secret.ciphertext(), secret.nonce(), secret.keyVersion(), "HMAC-SHA1", 6, 30,
                Timestamp.from(now), subject
        );
    }

    public void rotateCredentialKey(String subject, AdminAuthCrypto.EncryptedValue secret) {
        jdbc.update(
                "update admin_totp_credentials set encrypted_secret=?,nonce=?,key_version=? where subject_id=?",
                secret.ciphertext(), secret.nonce(), secret.keyVersion(), subject
        );
    }

    public boolean acceptTimestep(String subject, long timestep) {
        return jdbc.update(
                "update admin_totp_credentials set last_accepted_timestep=? where subject_id=? "
                        + "and (last_accepted_timestep is null or last_accepted_timestep<?)",
                timestep, subject, timestep
        ) == 1;
    }

    public Optional<Challenge> challenge(String hash) {
        return jdbc.query(
                "select subject_id,purpose,encrypted_secret,nonce,key_version,password_authenticated_at,expires_at,"
                        + "attempt_count,consumed_at from admin_auth_challenges where challenge_hash=?",
                (rs, n) -> new Challenge(
                        hash, rs.getString(1), rs.getString(2), rs.getString(3), rs.getBytes(4), rs.getString(5),
                        instant(rs.getTimestamp(6)), rs.getTimestamp(7).toInstant(), rs.getInt(8),
                        instant(rs.getTimestamp(9))
                ),
                hash
        ).stream().findFirst();
    }

    public void insertChallenge(
            String hash,
            String subject,
            String purpose,
            AdminAuthCrypto.EncryptedValue secret,
            Instant passwordAuthenticatedAt,
            Instant expires
    ) {
        jdbc.update(
                "insert into admin_auth_challenges(challenge_hash,subject_id,purpose,encrypted_secret,nonce,key_version,"
                        + "password_authenticated_at,expires_at) values(?,?,?,?,?,?,?,?)",
                hash, subject, purpose, secret == null ? null : secret.ciphertext(),
                secret == null ? null : secret.nonce(), secret == null ? null : secret.keyVersion(),
                timestamp(passwordAuthenticatedAt), Timestamp.from(expires)
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean incrementAttempt(String hash) {
        return jdbc.update(
                "update admin_auth_challenges set attempt_count=attempt_count+1 where challenge_hash=? "
                        + "and consumed_at is null and expires_at>? and attempt_count<5",
                hash, Timestamp.from(Instant.now())
        ) == 1;
    }

    public boolean consumeChallenge(String hash) {
        return jdbc.update(
                "update admin_auth_challenges set consumed_at=? where challenge_hash=? and consumed_at is null "
                        + "and expires_at>? and attempt_count<=5",
                Timestamp.from(Instant.now()), hash, Timestamp.from(Instant.now())
        ) == 1;
    }

    public void replaceRecoveryCodes(String subject, List<String> hashes, Instant now) {
        jdbc.update(
                "update admin_recovery_codes set replaced_at=? where subject_id=? and used_at is null and replaced_at is null",
                Timestamp.from(now), subject
        );
        for (String hash : hashes) {
            jdbc.update(
                    "insert into admin_recovery_codes(subject_id,code_hash,created_at) values(?,?,?)",
                    subject, hash, Timestamp.from(now)
            );
        }
    }

    public List<String> activeRecoveryHashes(String subject) {
        return jdbc.query(
                "select code_hash from admin_recovery_codes where subject_id=? and used_at is null and replaced_at is null",
                (rs, n) -> rs.getString(1), subject
        );
    }

    public boolean consumeRecoveryHash(String subject, String hash) {
        return jdbc.update(
                "update admin_recovery_codes set used_at=? where subject_id=? and code_hash=? "
                        + "and used_at is null and replaced_at is null",
                Timestamp.from(Instant.now()), subject, hash
        ) == 1;
    }

    public int activeRecoveryCount(String subject) {
        Integer count = jdbc.queryForObject(
                "select count(*) from admin_recovery_codes where subject_id=? and used_at is null and replaced_at is null",
                Integer.class, subject
        );
        return count == null ? 0 : count;
    }

    public void insertSession(
            String sessionHash,
            String subject,
            String scope,
            String environment,
            String authMode,
            String assurance,
            Instant passwordAuthenticatedAt,
            Instant totpAuthenticatedAt,
            String credentialKeyVersion,
            String passwordCredentialHash,
            Instant issued,
            Instant expires,
            Instant idleExpires
    ) {
        jdbc.update(
                "insert into admin_sessions(session_id,subject_id,scope,environment,auth_mode,assurance,"
                        + "password_authenticated_at,totp_authenticated_at,credential_key_version,password_credential_hash,"
                        + "issued_at,expires_at,idle_expires_at,last_seen_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                sessionHash, subject, scope, environment, authMode, assurance, timestamp(passwordAuthenticatedAt),
                timestamp(totpAuthenticatedAt), credentialKeyVersion, passwordCredentialHash,
                Timestamp.from(issued), Timestamp.from(expires),
                Timestamp.from(idleExpires), Timestamp.from(issued)
        );
    }

    public Optional<Session> touchActiveSession(
            String sessionHash,
            String environment,
            String authMode,
            String passwordCredentialHash,
            Instant nextFullIdleExpiry,
            Instant nextRecoveryIdleExpiry
    ) {
        Instant now = Instant.now();
        int updated = jdbc.update(
                "update admin_sessions set last_seen_at=?,idle_expires_at=least(expires_at,case when scope='RECOVERY' "
                        + "then ? else ? end) where session_id=? "
                        + "and environment=? and auth_mode=? and password_credential_hash=? "
                        + "and revoked_at is null and expires_at>? and idle_expires_at>?",
                Timestamp.from(now), Timestamp.from(nextRecoveryIdleExpiry), Timestamp.from(nextFullIdleExpiry),
                sessionHash, environment, authMode, passwordCredentialHash,
                Timestamp.from(now), Timestamp.from(now)
        );
        if (updated != 1) {
            return Optional.empty();
        }
        return jdbc.query(
                "select subject_id,scope,environment,auth_mode,assurance,password_authenticated_at,"
                        + "totp_authenticated_at,credential_key_version,expires_at from admin_sessions where session_id=?",
                (rs, n) -> new Session(
                        sessionHash, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), instant(rs.getTimestamp(6)), instant(rs.getTimestamp(7)), rs.getString(8),
                        rs.getTimestamp(9).toInstant()
                ),
                sessionHash
        ).stream().findFirst();
    }

    public boolean activeSessionWithScope(String sessionHash, String scope, String environment, String authMode,
                                          String passwordCredentialHash) {
        Instant now = Instant.now();
        Integer count = jdbc.queryForObject(
                "select count(*) from admin_sessions where session_id=? and scope=? and environment=? and auth_mode=? "
                        + "and password_credential_hash=? and revoked_at is null and expires_at>? and idle_expires_at>?",
                Integer.class, sessionHash, scope, environment, authMode, passwordCredentialHash,
                Timestamp.from(now), Timestamp.from(now)
        );
        return count != null && count == 1;
    }

    public void revokeSession(String sessionHash) {
        jdbc.update(
                "update admin_sessions set revoked_at=? where session_id=? and revoked_at is null",
                Timestamp.from(Instant.now()), sessionHash
        );
    }

    public void revokeAll(String subject) {
        jdbc.update(
                "update admin_sessions set revoked_at=? where subject_id=? and revoked_at is null",
                Timestamp.from(Instant.now()), subject
        );
    }

    public void insertOperationChallenge(
            String challengeHash,
            String subject,
            String sessionHash,
            String actionKey,
            String targetId,
            String source,
            Instant expiresAt
    ) {
        jdbc.update(
                "insert into admin_operation_challenges(challenge_hash,subject_id,session_id,action_key,target_id,"
                        + "source,expires_at) values(?,?,?,?,?,?,?)",
                challengeHash, subject, sessionHash, actionKey, targetId, source, Timestamp.from(expiresAt)
        );
    }

    public Optional<OperationChallenge> operationChallenge(String challengeHash) {
        return jdbc.query(
                "select subject_id,session_id,action_key,target_id,expires_at,attempt_count,consumed_at "
                        + "from admin_operation_challenges where challenge_hash=?",
                (rs, n) -> new OperationChallenge(
                        challengeHash, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getTimestamp(5).toInstant(), rs.getInt(6), instant(rs.getTimestamp(7))
                ),
                challengeHash
        ).stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean incrementOperationAttempt(String challengeHash) {
        return jdbc.update(
                "update admin_operation_challenges set attempt_count=attempt_count+1 where challenge_hash=? "
                        + "and consumed_at is null and expires_at>? and attempt_count<5",
                challengeHash, Timestamp.from(Instant.now())
        ) == 1;
    }

    public boolean consumeOperationChallenge(String challengeHash) {
        return jdbc.update(
                "update admin_operation_challenges set consumed_at=? where challenge_hash=? and consumed_at is null "
                        + "and expires_at>? and attempt_count<=5",
                Timestamp.from(Instant.now()), challengeHash, Timestamp.from(Instant.now())
        ) == 1;
    }

    public void insertOperationProof(
            String proofHash,
            String subject,
            String sessionHash,
            String actionKey,
            String targetId,
            Instant expiresAt
    ) {
        jdbc.update(
                "insert into admin_operation_proofs(proof_hash,subject_id,session_id,action_key,target_id,expires_at) "
                        + "values(?,?,?,?,?,?)",
                proofHash, subject, sessionHash, actionKey, targetId, Timestamp.from(expiresAt)
        );
    }

    public boolean consumeOperationProof(
            String proofHash,
            String subject,
            String sessionHash,
            String actionKey,
            String targetId
    ) {
        return jdbc.update(
                "update admin_operation_proofs set consumed_at=? where proof_hash=? and subject_id=? and session_id=? "
                        + "and action_key=? and target_id=? and consumed_at is null and expires_at>?",
                Timestamp.from(Instant.now()), proofHash, subject, sessionHash, actionKey, targetId,
                Timestamp.from(Instant.now())
        ) == 1;
    }

    public void clearAuthentication(String subject, Instant now) {
        jdbc.update("update admin_sessions set revoked_at=? where subject_id=? and revoked_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_recovery_codes set replaced_at=? where subject_id=? and used_at is null and replaced_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_auth_challenges set consumed_at=? where subject_id=? and consumed_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_operation_challenges set consumed_at=? where subject_id=? and consumed_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_operation_proofs set consumed_at=? where subject_id=? and consumed_at is null", Timestamp.from(now), subject);
        jdbc.update("delete from admin_totp_credentials where subject_id=?", subject);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(String event, String subject, String challengeHash, String source, String result, String reason) {
        jdbc.update(
                "insert into admin_auth_audit(event_type,subject_id,challenge_hash,source,result,reason_code,created_at) "
                        + "values(?,?,?,?,?,?,?)",
                event, subject, challengeHash, source, result, reason, Timestamp.from(Instant.now())
        );
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    public record Credential(
            String encryptedSecret,
            byte[] nonce,
            String keyVersion,
            String algorithm,
            int digits,
            int periodSeconds,
            Long lastAcceptedTimestep
    ) {}

    public record Challenge(
            String hash,
            String subject,
            String purpose,
            String encryptedSecret,
            byte[] nonce,
            String keyVersion,
            Instant passwordAuthenticatedAt,
            Instant expiresAt,
            int attempts,
            Instant consumedAt
    ) {}

    public record Session(
            String sessionHash,
            String subject,
            String scope,
            String environment,
            String authMode,
            String assurance,
            Instant passwordAuthenticatedAt,
            Instant totpAuthenticatedAt,
            String credentialKeyVersion,
            Instant expiresAt
    ) {}

    public record OperationChallenge(
            String hash,
            String subject,
            String sessionHash,
            String actionKey,
            String targetId,
            Instant expiresAt,
            int attempts,
            Instant consumedAt
    ) {}
}
