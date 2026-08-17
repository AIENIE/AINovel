-- v1.0 audit high-severity remediation. Keep V1-V13 immutable.

ALTER TABLE `manuscripts`
  ADD COLUMN `version` bigint NOT NULL DEFAULT 0;

CREATE TABLE `external_identities` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `issuer` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_uid` bigint NOT NULL,
  `verified_username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_identity_issuer_uid` (`issuer`,`remote_uid`),
  UNIQUE KEY `uk_external_identity_user_issuer` (`user_id`,`issuer`),
  CONSTRAINT `fk_external_identity_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `external_identities` (`id`, `user_id`, `issuer`, `remote_uid`, `verified_username`, `created_at`, `updated_at`)
SELECT UUID_TO_BIN(UUID()), `id`, 'aienie-user-service', `remote_uid`, `username`, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `users`
WHERE `remote_uid` IS NOT NULL;

CREATE TABLE `ai_credit_reservations` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_id` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reserved_amount` bigint NOT NULL,
  `settled_amount` bigint NOT NULL DEFAULT 0,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_content` longtext COLLATE utf8mb4_unicode_ci,
  `prompt_tokens` bigint NOT NULL DEFAULT 0,
  `completion_tokens` bigint NOT NULL DEFAULT 0,
  `cache_tokens` bigint NOT NULL DEFAULT 0,
  `attempt_count` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_reservation_user_key` (`user_id`,`idempotency_key`),
  KEY `idx_ai_reservation_status_updated` (`status`,`updated_at`),
  CONSTRAINT `fk_ai_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

UPDATE `project_credit_ledger` duplicate_row
JOIN `project_credit_ledger` canonical_row
  ON canonical_row.`user_id` = duplicate_row.`user_id`
 AND canonical_row.`idempotency_key` = duplicate_row.`idempotency_key`
 AND canonical_row.`id` < duplicate_row.`id`
SET duplicate_row.`idempotency_key` = NULL
WHERE duplicate_row.`idempotency_key` IS NOT NULL;

ALTER TABLE `project_credit_ledger`
  ADD UNIQUE KEY `uk_project_credit_ledger_user_idempotency` (`user_id`,`idempotency_key`);

ALTER TABLE `credit_conversion_orders`
  ADD COLUMN `attempt_count` int NOT NULL DEFAULT 0,
  ADD COLUMN `next_attempt_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `lease_expires_at` datetime(6) DEFAULT NULL,
  ADD KEY `idx_conversion_recovery` (`status`,`next_attempt_at`,`lease_expires_at`);

ALTER TABLE `ai_operation_runs`
  ADD COLUMN `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `active_scope_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `lease_expires_at` datetime(6) DEFAULT NULL,
  ADD UNIQUE KEY `uk_ai_operation_user_idempotency` (`user_id`,`idempotency_key`),
  ADD UNIQUE KEY `uk_ai_operation_active_scope` (`active_scope_key`),
  ADD KEY `idx_ai_operation_dispatch` (`status`,`lease_expires_at`);

UPDATE `ai_operation_runs`
SET `status` = CASE WHEN `stream_started` = b'1' THEN 'RECOVERY_REQUIRED' ELSE 'QUEUED' END,
    `error_message` = CASE WHEN `stream_started` = b'1' THEN '服务升级时 AI 调用状态不确定，请确认后重试' ELSE NULL END
WHERE `status` IN ('RUNNING', 'STREAMING');

ALTER TABLE `g2_evaluation_samples`
  ADD COLUMN `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `lease_expires_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `attempt_count` int NOT NULL DEFAULT 0,
  ADD KEY `idx_g2_sample_dispatch` (`status`,`lease_expires_at`);

UPDATE `g2_evaluation_samples` SET `status` = 'PENDING' WHERE `status` = 'RUNNING';

ALTER TABLE `export_jobs`
  ADD COLUMN `snapshot_json` longtext COLLATE utf8mb4_unicode_ci,
  ADD COLUMN `content_blob` longblob,
  ADD COLUMN `checksum` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `started_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `completed_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  ADD COLUMN `lease_expires_at` datetime(6) DEFAULT NULL,
  ADD COLUMN `attempt_count` int NOT NULL DEFAULT 0,
  ADD KEY `idx_export_job_dispatch` (`status`,`lease_expires_at`);

UPDATE `export_jobs`
SET `status` = 'failed', `progress` = 100, `error_message` = 'EXPORT_RECREATE_REQUIRED', `completed_at` = CURRENT_TIMESTAMP(6)
WHERE `status` NOT IN ('completed', 'failed', 'expired', 'cancelled');

CREATE TABLE `material_chunks` (
  `chunk_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `material_id` binary(16) NOT NULL,
  `owner_user_id` binary(16) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci,
  `text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags` text COLLATE utf8mb4_unicode_ci,
  `chunk_seq` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`chunk_id`),
  KEY `idx_material_chunk_visibility` (`status`,`owner_user_id`,`material_id`),
  CONSTRAINT `fk_material_chunk_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_material_chunk_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_index_jobs` (
  `id` binary(16) NOT NULL,
  `material_id` binary(16) NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_index_job_material` (`material_id`),
  KEY `idx_material_index_dispatch` (`status`,`next_attempt_at`,`lease_expires_at`),
  CONSTRAINT `fk_material_index_job_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `material_index_jobs` (`id`,`material_id`,`status`,`attempt_count`,`next_attempt_at`,`created_at`,`updated_at`)
SELECT UUID_TO_BIN(UUID()), `id`, 'queued', 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM `materials`;
