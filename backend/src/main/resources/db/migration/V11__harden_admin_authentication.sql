UPDATE admin_sessions
SET revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP(6));

ALTER TABLE admin_sessions
  MODIFY COLUMN session_id CHAR(64) NOT NULL,
  ADD COLUMN environment VARCHAR(16) NOT NULL DEFAULT 'legacy' AFTER scope,
  ADD COLUMN auth_mode VARCHAR(16) NOT NULL DEFAULT 'legacy' AFTER environment,
  ADD COLUMN assurance VARCHAR(32) NOT NULL DEFAULT 'legacy' AFTER auth_mode,
  ADD COLUMN password_authenticated_at DATETIME(6) NULL AFTER assurance,
  ADD COLUMN totp_authenticated_at DATETIME(6) NULL AFTER password_authenticated_at,
  ADD COLUMN credential_key_version VARCHAR(64) NULL AFTER totp_authenticated_at,
  ADD COLUMN password_credential_hash CHAR(64) NOT NULL DEFAULT 'legacy' AFTER credential_key_version;

ALTER TABLE admin_auth_challenges
  ADD COLUMN password_authenticated_at DATETIME(6) NULL AFTER key_version;

CREATE TABLE admin_operation_challenges (
  challenge_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  session_id CHAR(64) NOT NULL,
  action_key VARCHAR(255) NOT NULL,
  target_id VARCHAR(512) NOT NULL,
  source VARCHAR(255) NULL,
  expires_at DATETIME(6) NOT NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (challenge_hash),
  KEY ix_admin_operation_challenge_session (session_id, expires_at, consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_operation_proofs (
  proof_hash CHAR(64) NOT NULL,
  subject_id VARCHAR(255) NOT NULL,
  session_id CHAR(64) NOT NULL,
  action_key VARCHAR(255) NOT NULL,
  target_id VARCHAR(512) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  consumed_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (proof_hash),
  KEY ix_admin_operation_proof_session (session_id, expires_at, consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
