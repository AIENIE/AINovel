CREATE TABLE IF NOT EXISTS admin_emergency_subjects (
 subject_id VARCHAR(255) PRIMARY KEY, credential_version BIGINT NOT NULL DEFAULT 1
) ENGINE=InnoDB;
INSERT IGNORE INTO admin_emergency_subjects(subject_id) VALUES ('configured-admin');
CREATE TABLE IF NOT EXISTS admin_emergency_codes (
 record_id CHAR(36) PRIMARY KEY, subject_id VARCHAR(255) NOT NULL, code_hash VARCHAR(255) NOT NULL,
 encrypted_code TEXT NOT NULL, nonce VARBINARY(32) NOT NULL, key_version VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL, used_at DATETIME(6) NULL,
 KEY ix_emergency_subject(subject_id,used_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS admin_emergency_challenge_bindings (
 challenge_hash CHAR(64) PRIMARY KEY, credential_version BIGINT NOT NULL, recovery_session CHAR(64) NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS admin_emergency_migrations (migration_id VARCHAR(64) PRIMARY KEY);
UPDATE admin_recovery_codes SET replaced_at=COALESCE(replaced_at,CURRENT_TIMESTAMP(6))
 WHERE NOT EXISTS (SELECT 1 FROM admin_emergency_migrations WHERE migration_id='retained-v1');
UPDATE admin_sessions SET revoked_at=COALESCE(revoked_at,CURRENT_TIMESTAMP(6)) WHERE scope='RECOVERY'
 AND NOT EXISTS (SELECT 1 FROM admin_emergency_migrations WHERE migration_id='retained-v1');
UPDATE admin_auth_challenges SET consumed_at=COALESCE(consumed_at,CURRENT_TIMESTAMP(6))
 WHERE NOT EXISTS (SELECT 1 FROM admin_emergency_migrations WHERE migration_id='retained-v1');
INSERT IGNORE INTO admin_emergency_migrations(migration_id) VALUES ('retained-v1');
