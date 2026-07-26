CREATE TABLE admin_totp_credentials (
  id BIGINT NOT NULL AUTO_INCREMENT,
  subject_id VARCHAR(255) NOT NULL,
  encrypted_secret TEXT NOT NULL,
  nonce VARBINARY(32) NOT NULL,
  key_version VARCHAR(64) NOT NULL,
  algorithm VARCHAR(32) NOT NULL,
  digits INT NOT NULL,
  period_seconds INT NOT NULL,
  last_accepted_timestep BIGINT NULL,
  enabled_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_totp_subject (subject_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_auth_challenges (
  challenge_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  encrypted_secret TEXT NULL,
  nonce VARBINARY(32) NULL,
  key_version VARCHAR(64) NULL,
  expires_at DATETIME(6) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  consumed_at DATETIME(6) NULL,
  PRIMARY KEY (challenge_hash),
  KEY ix_admin_auth_challenge_subject (subject_id, purpose, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_recovery_codes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  subject_id VARCHAR(255) NOT NULL,
  code_hash VARCHAR(255) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  replaced_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  KEY ix_admin_recovery_subject (subject_id, used_at, replaced_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_sessions (
  session_id CHAR(36) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  scope VARCHAR(32) NOT NULL,
  issued_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  idle_expires_at DATETIME(6) NOT NULL,
  last_seen_at DATETIME(6) NOT NULL,
  revoked_at DATETIME(6) NULL,
  PRIMARY KEY (session_id),
  KEY ix_admin_sessions_subject (subject_id, revoked_at, expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_auth_audit (
  id BIGINT NOT NULL AUTO_INCREMENT,
  event_type VARCHAR(64) NOT NULL,
  subject_id VARCHAR(255) NULL,
  challenge_hash CHAR(64) NULL,
  source VARCHAR(255) NULL,
  result VARCHAR(32) NOT NULL,
  reason_code VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY ix_admin_auth_audit_created (created_at),
  KEY ix_admin_auth_audit_subject (subject_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
