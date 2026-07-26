package com.ainovel.app.adminauth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminAuthStore {
    private final JdbcTemplate jdbc;
    public AdminAuthStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean credentialExists(String subject) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from admin_totp_credentials where subject_id=?)", Boolean.class, subject));
    }

    public Optional<Credential> credential(String subject) {
        return jdbc.query("select encrypted_secret, nonce, key_version, algorithm, digits, period_seconds, last_accepted_timestep from admin_totp_credentials where subject_id=?", (rs, n) ->
                new Credential(rs.getString(1), rs.getBytes(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6), (Long) rs.getObject(7)), subject).stream().findFirst();
    }

    public void insertCredential(String subject, AdminAuthCrypto.EncryptedValue secret, Instant now) {
        jdbc.update("insert into admin_totp_credentials(subject_id,encrypted_secret,nonce,key_version,algorithm,digits,period_seconds,last_accepted_timestep,enabled_at) values(?,?,?,?,?,?,?,?,?)", subject, secret.ciphertext(), secret.nonce(), secret.keyVersion(), "HMAC-SHA1", 6, 30, null, Timestamp.from(now));
    }

    public int replaceCredential(String subject, AdminAuthCrypto.EncryptedValue secret, Instant now) {
        return jdbc.update("update admin_totp_credentials set encrypted_secret=?,nonce=?,key_version=?,algorithm=?,digits=?,period_seconds=?,last_accepted_timestep=null,enabled_at=? where subject_id=?", secret.ciphertext(), secret.nonce(), secret.keyVersion(), "HMAC-SHA1", 6, 30, Timestamp.from(now), subject);
    }

    public void rotateCredentialKey(String subject, AdminAuthCrypto.EncryptedValue secret) {
        jdbc.update(
                "update admin_totp_credentials set encrypted_secret=?, nonce=?, key_version=? where subject_id=?",
                secret.ciphertext(),
                secret.nonce(),
                secret.keyVersion(),
                subject
        );
    }

    public boolean acceptTimestep(String subject, long timestep) {
        return jdbc.update("update admin_totp_credentials set last_accepted_timestep=? where subject_id=? and (last_accepted_timestep is null or last_accepted_timestep<?)", timestep, subject, timestep) == 1;
    }

    public Optional<Challenge> challenge(String hash) {
        return jdbc.query("select subject_id,purpose,encrypted_secret,nonce,key_version,expires_at,attempt_count,consumed_at from admin_auth_challenges where challenge_hash=?", (rs, n) -> new Challenge(hash, rs.getString(1), rs.getString(2), rs.getString(3), rs.getBytes(4), rs.getString(5), rs.getTimestamp(6).toInstant(), rs.getInt(7), rs.getTimestamp(8) == null ? null : rs.getTimestamp(8).toInstant()), hash).stream().findFirst();
    }

    public void insertChallenge(String hash, String subject, String purpose, AdminAuthCrypto.EncryptedValue secret, Instant expires) {
        jdbc.update("insert into admin_auth_challenges(challenge_hash,subject_id,purpose,encrypted_secret,nonce,key_version,expires_at) values(?,?,?,?,?,?,?)", hash, subject, purpose, secret == null ? null : secret.ciphertext(), secret == null ? null : secret.nonce(), secret == null ? null : secret.keyVersion(), Timestamp.from(expires));
    }

    public boolean incrementAttempt(String hash) {
        return jdbc.update("update admin_auth_challenges set attempt_count=attempt_count+1 where challenge_hash=? and consumed_at is null and expires_at>? and attempt_count<5", hash, Timestamp.from(Instant.now())) == 1;
    }

    public boolean consumeChallenge(String hash) {
        return jdbc.update("update admin_auth_challenges set consumed_at=? where challenge_hash=? and consumed_at is null and expires_at>? and attempt_count<=5", Timestamp.from(Instant.now()), hash, Timestamp.from(Instant.now())) == 1;
    }

    public void replaceRecoveryCodes(String subject, List<String> hashes, Instant now) {
        jdbc.update("update admin_recovery_codes set replaced_at=? where subject_id=? and used_at is null and replaced_at is null", Timestamp.from(now), subject);
        for (String hash : hashes) jdbc.update("insert into admin_recovery_codes(subject_id,code_hash,created_at) values(?,?,?)", subject, hash, Timestamp.from(now));
    }

    public List<String> activeRecoveryHashes(String subject) {
        return jdbc.query("select code_hash from admin_recovery_codes where subject_id=? and used_at is null and replaced_at is null", (rs, n) -> rs.getString(1), subject);
    }

    public boolean consumeRecoveryHash(String subject, String hash) {
        return jdbc.update("update admin_recovery_codes set used_at=? where subject_id=? and code_hash=? and used_at is null and replaced_at is null", Timestamp.from(Instant.now()), subject, hash) == 1;
    }

    public int activeRecoveryCount(String subject) {
        Integer count = jdbc.queryForObject("select count(*) from admin_recovery_codes where subject_id=? and used_at is null and replaced_at is null", Integer.class, subject);
        return count == null ? 0 : count;
    }

    public void insertSession(
            String id,
            String subject,
            String scope,
            Instant issued,
            Instant expires,
            Instant idleExpires
    ) {
        jdbc.update(
                "insert into admin_sessions(session_id,subject_id,scope,issued_at,expires_at,idle_expires_at,last_seen_at) values(?,?,?,?,?,?,?)",
                id,
                subject,
                scope,
                Timestamp.from(issued),
                Timestamp.from(expires),
                Timestamp.from(idleExpires),
                Timestamp.from(issued)
        );
    }

    public boolean touchActiveSession(String id, String scope, Instant nextIdleExpiry) {
        Instant now = Instant.now();
        return jdbc.update(
                "update admin_sessions set last_seen_at=?, idle_expires_at=? "
                        + "where session_id=? and scope=? and revoked_at is null and expires_at>? and idle_expires_at>?",
                Timestamp.from(now),
                Timestamp.from(nextIdleExpiry),
                id,
                scope,
                Timestamp.from(now),
                Timestamp.from(now)
        ) == 1;
    }

    public void revokeSession(String id) { jdbc.update("update admin_sessions set revoked_at=? where session_id=? and revoked_at is null", Timestamp.from(Instant.now()), id); }
    public void revokeAll(String subject) { jdbc.update("update admin_sessions set revoked_at=? where subject_id=? and revoked_at is null", Timestamp.from(Instant.now()), subject); }

    public void clearAuthentication(String subject, Instant now) {
        jdbc.update("update admin_sessions set revoked_at=? where subject_id=? and revoked_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_recovery_codes set replaced_at=? where subject_id=? and used_at is null and replaced_at is null", Timestamp.from(now), subject);
        jdbc.update("update admin_auth_challenges set consumed_at=? where subject_id=? and consumed_at is null", Timestamp.from(now), subject);
        jdbc.update("delete from admin_totp_credentials where subject_id=?", subject);
    }

    public void audit(String event, String subject, String challengeHash, String source, String result, String reason) {
        jdbc.update("insert into admin_auth_audit(event_type,subject_id,challenge_hash,source,result,reason_code,created_at) values(?,?,?,?,?,?,?)", event, subject, challengeHash, source, result, reason, Timestamp.from(Instant.now()));
    }

    public record Credential(String encryptedSecret, byte[] nonce, String keyVersion, String algorithm, int digits, int periodSeconds, Long lastAcceptedTimestep) {}
    public record Challenge(String hash, String subject, String purpose, String encryptedSecret, byte[] nonce, String keyVersion, Instant expiresAt, int attempts, Instant consumedAt) {}
}
