-- MySQL V50 reference structure from the V35 upgrade replay.

CREATE TABLE `admin_auth_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `event_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `challenge_hash` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_admin_auth_audit_created` (`created_at`),
  KEY `ix_admin_auth_audit_subject` (`subject_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_auth_challenges` (
  `challenge_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_secret` text COLLATE utf8mb4_unicode_ci,
  `nonce` varbinary(32) DEFAULT NULL,
  `key_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_authenticated_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `consumed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`challenge_hash`),
  KEY `ix_admin_auth_challenge_subject` (`subject_id`,`purpose`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_emergency_challenge_bindings` (
  `challenge_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credential_version` bigint NOT NULL,
  `recovery_session` char(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`challenge_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_emergency_codes` (
  `record_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_code` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varbinary(32) NOT NULL,
  `key_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`record_id`),
  KEY `ix_emergency_subject` (`subject_id`,`used_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_emergency_migrations` (
  `migration_id` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`migration_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_emergency_subjects` (
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credential_version` bigint NOT NULL DEFAULT '1',
  PRIMARY KEY (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_operation_challenges` (
  `challenge_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime(6) NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`challenge_hash`),
  KEY `ix_admin_operation_challenge_session` (`session_id`,`expires_at`,`consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_operation_proofs` (
  `proof_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `session_id` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_id` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `consumed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`proof_hash`),
  KEY `ix_admin_operation_proof_session` (`session_id`,`expires_at`,`consumed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_recovery_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `replaced_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_admin_recovery_subject` (`subject_id`,`used_at`,`replaced_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_sessions` (
  `session_id` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `environment` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `auth_mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `assurance` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `password_authenticated_at` datetime(6) DEFAULT NULL,
  `totp_authenticated_at` datetime(6) DEFAULT NULL,
  `credential_key_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_credential_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'legacy',
  `issued_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `idle_expires_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`session_id`),
  KEY `ix_admin_sessions_subject` (`subject_id`,`revoked_at`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `admin_totp_credentials` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `subject_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `encrypted_secret` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `nonce` varbinary(32) NOT NULL,
  `key_version` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `algorithm` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `digits` int NOT NULL,
  `period_seconds` int NOT NULL,
  `last_accepted_timestep` bigint DEFAULT NULL,
  `enabled_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_admin_totp_subject` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_credit_reservations` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reference_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `reserved_amount` bigint NOT NULL,
  `settled_amount` bigint NOT NULL DEFAULT '0',
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `prompt_tokens` bigint NOT NULL DEFAULT '0',
  `completion_tokens` bigint NOT NULL DEFAULT '0',
  `cache_tokens` bigint NOT NULL DEFAULT '0',
  `attempt_count` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `selection_json` longtext COLLATE utf8mb4_unicode_ci,
  `actual_model_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `frozen_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `request_identity` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `result_metadata_json` longtext COLLATE utf8mb4_unicode_ci,
  `result_native_response_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_reservation_user_key` (`user_id`,`idempotency_key`),
  KEY `idx_ai_reservation_status_updated` (`status`,`updated_at`),
  KEY `idx_ai_reservation_operation` (`operation_id`,`status`),
  CONSTRAINT `fk_ai_reservation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_model_catalog_state` (
  `id` int NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `last_attempt_at` timestamp(6) NULL DEFAULT NULL,
  `last_success_at` timestamp(6) NULL DEFAULT NULL,
  `last_error` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_model_options` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_id` binary(16) NOT NULL,
  `settings_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `active` bit(1) NOT NULL,
  `position_index` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_ai_option_model` (`model_id`),
  KEY `idx_ai_option_feature` (`task_type`,`active`,`position_index`),
  CONSTRAINT `fk_ai_option_model` FOREIGN KEY (`model_id`) REFERENCES `model_registry` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_model_quotes` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `input_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_path` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `business_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `configuration_revision` bigint NOT NULL,
  `selections_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `accepted_request_key` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_quote_user_expiry` (`user_id`,`expires_at`),
  CONSTRAINT `fk_ai_quote_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_operation_runs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `operation_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scope_id` binary(16) DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_step` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_steps` int NOT NULL DEFAULT '1',
  `completed_steps` int NOT NULL DEFAULT '0',
  `output_tokens` bigint NOT NULL DEFAULT '0',
  `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1',
  `attempt_count` int NOT NULL DEFAULT '0',
  `stream_started` bit(1) NOT NULL DEFAULT b'0',
  `request_id` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `active_scope_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `model_selections_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_operation_user_idempotency` (`user_id`,`idempotency_key`),
  UNIQUE KEY `uk_ai_operation_active_scope` (`active_scope_key`),
  KEY `idx_ai_operation_user_updated` (`user_id`,`updated_at`),
  KEY `idx_ai_operation_scope_status` (`scope_type`,`scope_id`,`status`),
  KEY `idx_ai_operation_status_updated` (`status`,`updated_at`),
  KEY `idx_ai_operation_dispatch` (`status`,`lease_expires_at`),
  CONSTRAINT `fk_ai_operation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_operation_steps` (
  `id` binary(16) NOT NULL,
  `operation_id` binary(16) NOT NULL,
  `step_index` int NOT NULL,
  `step_key` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `step_label` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_tokens` bigint NOT NULL DEFAULT '0',
  `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1',
  `prompt_tokens` bigint NOT NULL DEFAULT '0',
  `completion_tokens` bigint NOT NULL DEFAULT '0',
  `cache_tokens` bigint NOT NULL DEFAULT '0',
  `request_id` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_operation_step` (`operation_id`,`step_index`),
  CONSTRAINT `fk_ai_operation_step_run` FOREIGN KEY (`operation_id`) REFERENCES `ai_operation_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_validation_budgets` (
  `id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `used` int NOT NULL DEFAULT '0',
  `call_limit` int NOT NULL,
  `provider_attempt_limit` int DEFAULT NULL,
  `reserved_provider_attempts` int NOT NULL DEFAULT '0',
  `currency` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cost_limit` decimal(19,6) DEFAULT NULL,
  `settled_cost` decimal(19,6) NOT NULL DEFAULT '0.000000',
  `reserved_cost` decimal(19,6) NOT NULL DEFAULT '0.000000',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `ai_validation_calls` (
  `id` binary(16) NOT NULL,
  `run_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt` int NOT NULL,
  `request_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `operation_kind` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reserved_provider_attempts` int NOT NULL DEFAULT '0',
  `budget_id` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `currency` varchar(3) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price_version_id` bigint DEFAULT NULL,
  `price_sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `max_cost` decimal(19,6) DEFAULT NULL,
  `settled_cost` decimal(19,6) DEFAULT NULL,
  `settlement_state` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_validation_attempt` (`run_id`,`attempt`),
  KEY `idx_validation_call_request` (`run_id`,`request_id`),
  CONSTRAINT `fk_validation_run` FOREIGN KEY (`run_id`) REFERENCES `ai_validation_budgets` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `analysis_jobs` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `job_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_reference` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT '0',
  `progress_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_reference` binary(16) DEFAULT NULL,
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_analysis_job_story` (`story_id`),
  KEY `idx_analysis_job_user` (`user_id`),
  CONSTRAINT `fk_analysis_job_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_analysis_job_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `async_jobs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `scope_id` binary(16) NOT NULL,
  `job_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `step` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT '0',
  `output_tokens` bigint NOT NULL DEFAULT '0',
  `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1',
  `attempt_count` int NOT NULL DEFAULT '0',
  `idempotency_key` varchar(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `charged_credits` bigint NOT NULL DEFAULT '0',
  `remaining_credits` bigint NOT NULL DEFAULT '0',
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_until` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `model_selections_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_async_job_idempotency` (`idempotency_key`),
  KEY `idx_async_job_scope_created` (`scope_id`,`created_at`),
  KEY `idx_async_job_status_lease` (`status`,`lease_until`),
  KEY `fk_async_job_user` (`user_id`),
  CONSTRAINT `fk_async_job_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `auto_save_config` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `auto_save_interval_seconds` int NOT NULL DEFAULT '300',
  `max_auto_versions` int NOT NULL DEFAULT '100',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auto_save_user` (`user_id`),
  CONSTRAINT `fk_auto_save_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `beta_reader_reports` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `scope` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_reference` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `analysis_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `summary` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `score_overall` int DEFAULT NULL,
  `score_pacing` int DEFAULT NULL,
  `score_characters` int DEFAULT NULL,
  `score_dialogue` int DEFAULT NULL,
  `score_consistency` int DEFAULT NULL,
  `score_engagement` int DEFAULT NULL,
  `token_cost` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_beta_story` (`story_id`),
  KEY `idx_beta_user` (`user_id`),
  CONSTRAINT `fk_beta_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_beta_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `character_cards` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `details` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `relationships` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `synopsis` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `role` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `archetype` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FK8fl6goog1m0be12r3huk0cxbu` (`story_id`),
  CONSTRAINT `fk_character_card_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `character_voices` (
  `id` binary(16) NOT NULL,
  `character_card_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `speech_pattern` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `vocabulary_level` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `catchphrases_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `emotional_range_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `dialect` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sample_dialogues_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `ai_analysis_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_character_voice_character` (`character_card_id`),
  KEY `idx_character_voice_story` (`story_id`),
  CONSTRAINT `fk_character_voice_character` FOREIGN KEY (`character_card_id`) REFERENCES `character_cards` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_character_voice_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `checkin_records` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `checkin_date` date NOT NULL,
  `reward` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_checkin_user_date` (`user_id`,`checkin_date`),
  CONSTRAINT `fk_checkin_record_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `continuity_issues` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `report_id` binary(16) DEFAULT NULL,
  `issue_type` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `evidence_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `suggestion` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_continuity_story` (`story_id`),
  KEY `idx_continuity_report` (`report_id`),
  CONSTRAINT `fk_continuity_report` FOREIGN KEY (`report_id`) REFERENCES `beta_reader_reports` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_continuity_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `creation_workflow_runs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `world_id` binary(16) DEFAULT NULL,
  `outline_id` binary(16) DEFAULT NULL,
  `active_job_id` binary(16) DEFAULT NULL,
  `template_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_step` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `seed_idea` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `genre` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tone` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_chapter_count` int NOT NULL DEFAULT '6',
  `auto_run` bit(1) NOT NULL DEFAULT b'0',
  `steps_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `model_selections_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_creation_workflow_user_updated` (`user_id`,`updated_at`),
  KEY `idx_creation_workflow_status` (`status`,`updated_at`),
  KEY `fk_creation_workflow_story` (`story_id`),
  KEY `fk_creation_workflow_world` (`world_id`),
  KEY `fk_creation_workflow_outline` (`outline_id`),
  CONSTRAINT `fk_creation_workflow_outline` FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_creation_workflow_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_creation_workflow_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_creation_workflow_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `credit_conversion_orders` (
  `id` binary(16) NOT NULL,
  `order_no` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_amount` bigint NOT NULL,
  `converted_amount` bigint NOT NULL,
  `project_before` bigint NOT NULL,
  `project_after` bigint NOT NULL,
  `public_before` bigint NOT NULL,
  `public_after` bigint NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_request_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remote_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion_order_no` (`order_no`),
  UNIQUE KEY `uk_conversion_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_conversion_recovery` (`status`,`next_attempt_at`,`lease_expires_at`),
  CONSTRAINT `fk_conversion_order_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `entity_extractions` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) DEFAULT NULL,
  `entity_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attributes_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `source_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `confidence` double NOT NULL DEFAULT '0',
  `reviewed` bit(1) NOT NULL,
  `review_action` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linked_lorebook_id` binary(16) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_extraction_story` (`story_id`),
  KEY `idx_extraction_manuscript` (`manuscript_id`),
  KEY `fk_extraction_lorebook` (`linked_lorebook_id`),
  CONSTRAINT `fk_extraction_lorebook` FOREIGN KEY (`linked_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_extraction_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_extraction_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `export_jobs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `template_id` binary(16) DEFAULT NULL,
  `format` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `chapter_range` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT '0',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_name` varchar(300) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size_bytes` bigint DEFAULT NULL,
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `snapshot_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `content_blob` longblob,
  `checksum` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_export_job_user` (`user_id`),
  KEY `idx_export_job_story` (`story_id`),
  KEY `idx_export_job_manuscript` (`manuscript_id`),
  KEY `fk_export_job_template` (`template_id`),
  KEY `idx_export_job_dispatch` (`status`,`lease_expires_at`),
  CONSTRAINT `fk_export_job_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_job_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_job_template` FOREIGN KEY (`template_id`) REFERENCES `export_templates` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_export_job_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `export_templates` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `format` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `is_default` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_export_template_user` (`user_id`),
  CONSTRAINT `fk_export_template_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `external_identities` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `issuer` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_uid` bigint NOT NULL,
  `verified_username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_external_identity_issuer_uid` (`issuer`,`remote_uid`),
  UNIQUE KEY `uk_external_identity_user_issuer` (`user_id`,`issuer`),
  CONSTRAINT `fk_external_identity_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_experiments` (
  `id` binary(16) NOT NULL,
  `title` varchar(180) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` binary(16) DEFAULT NULL,
  `created_by_admin_subject` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `minimum_votes` int NOT NULL DEFAULT '100',
  `minimum_sample_pairs` int NOT NULL DEFAULT '20',
  `minimum_reviewers` int NOT NULL DEFAULT '10',
  `crafted_win_rate_target` decimal(5,2) NOT NULL DEFAULT '55.00',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_g2_experiment_status` (`status`),
  KEY `fk_g2_experiment_creator` (`created_by`),
  CONSTRAINT `fk_g2_experiment_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT,
  CONSTRAINT `chk_g2_experiment_creator_exactly_one` CHECK ((((`created_by` is not null) and (`created_by_admin_subject` is null)) or ((`created_by` is null) and (`created_by_admin_subject` is not null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_invites` (
  `id` binary(16) NOT NULL,
  `experiment_id` binary(16) NOT NULL,
  `reviewer_id` binary(16) NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `accepted_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_g2_invite_experiment_reviewer` (`experiment_id`,`reviewer_id`),
  KEY `idx_g2_invite_reviewer` (`reviewer_id`,`status`),
  CONSTRAINT `fk_g2_invite_experiment` FOREIGN KEY (`experiment_id`) REFERENCES `g2_evaluation_experiments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_invite_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_samples` (
  `id` binary(16) NOT NULL,
  `experiment_id` binary(16) NOT NULL,
  `author_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `fast_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `crafted_text` mediumtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `failure_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_g2_sample_source` (`experiment_id`,`author_id`,`manuscript_id`,`scene_id`),
  KEY `idx_g2_sample_experiment_status` (`experiment_id`,`status`),
  KEY `idx_g2_sample_author` (`author_id`),
  KEY `fk_g2_sample_manuscript` (`manuscript_id`),
  KEY `idx_g2_sample_dispatch` (`status`,`lease_expires_at`),
  CONSTRAINT `fk_g2_sample_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_sample_experiment` FOREIGN KEY (`experiment_id`) REFERENCES `g2_evaluation_experiments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_sample_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_votes` (
  `id` binary(16) NOT NULL,
  `experiment_id` binary(16) NOT NULL,
  `sample_id` binary(16) NOT NULL,
  `reviewer_id` binary(16) NOT NULL,
  `choice` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_g2_vote_sample_reviewer` (`sample_id`,`reviewer_id`),
  KEY `idx_g2_vote_experiment` (`experiment_id`,`created_at`),
  KEY `idx_g2_vote_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_g2_vote_experiment` FOREIGN KEY (`experiment_id`) REFERENCES `g2_evaluation_experiments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_vote_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_vote_sample` FOREIGN KEY (`sample_id`) REFERENCES `g2_evaluation_samples` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `global_settings` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `maintenance_mode` bit(1) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `keyboard_shortcuts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `action` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_custom` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shortcut_user_action` (`user_id`,`action`),
  CONSTRAINT `fk_shortcut_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `knowledge_graph_relationships` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `source_lorebook_id` binary(16) NOT NULL,
  `target_lorebook_id` binary(16) NOT NULL,
  `relation_type` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kg_story` (`story_id`),
  KEY `idx_kg_source` (`source_lorebook_id`),
  KEY `idx_kg_target` (`target_lorebook_id`),
  CONSTRAINT `fk_kg_source` FOREIGN KEY (`source_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kg_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kg_target` FOREIGN KEY (`target_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `language_quality_decisions` (
  `id` binary(16) NOT NULL,
  `patch_id` binary(16) NOT NULL,
  `request_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(12) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_language_decision_key` (`patch_id`,`request_key`),
  CONSTRAINT `fk_language_decision_patch` FOREIGN KEY (`patch_id`) REFERENCES `language_quality_patches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `language_quality_patches` (
  `id` binary(16) NOT NULL,
  `report_id` binary(16) NOT NULL,
  `issue_id` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `data_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_language_patch_issue` (`report_id`,`issue_id`),
  CONSTRAINT `fk_language_patch_report` FOREIGN KEY (`report_id`) REFERENCES `language_quality_reports` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `language_quality_reports` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `source_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `data_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_version` bigint NOT NULL DEFAULT '0',
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_language_report_source` (`manuscript_id`,`scene_id`,`source_key`),
  KEY `ix_language_report_scene` (`manuscript_id`,`scene_id`,`created_at`),
  CONSTRAINT `fk_language_report_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `language_quality_settings` (
  `story_id` binary(16) NOT NULL,
  `generation_standard` tinyint(1) NOT NULL DEFAULT '0',
  `check_after_generation` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`story_id`),
  CONSTRAINT `fk_language_settings_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `lorebook_entries` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `entry_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `keywords_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `priority` int NOT NULL DEFAULT '0',
  `enabled` bit(1) NOT NULL,
  `insertion_position` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_budget` int NOT NULL DEFAULT '500',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lorebook_story` (`story_id`),
  KEY `idx_lorebook_user` (`user_id`),
  CONSTRAINT `fk_lorebook_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lorebook_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscript_branches` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `source_version_id` binary(16) DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_main` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_branch_manuscript_name` (`manuscript_id`,`name`),
  KEY `idx_branch_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_branch_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscript_content_migration` (
  `manuscript_id` binary(16) NOT NULL,
  `scene_count` int NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `verified_at` datetime(6) NOT NULL,
  PRIMARY KEY (`manuscript_id`),
  CONSTRAINT `fk_content_migration_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscript_creation_receipts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `outline_id` binary(16) NOT NULL,
  `request_key` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `manuscript_id` binary(16) DEFAULT NULL,
  `response_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_manuscript_creation_request` (`user_id`,`outline_id`,`request_key`),
  KEY `fk_creation_outline` (`outline_id`),
  KEY `fk_creation_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_creation_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_creation_outline` FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_creation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscript_scene_contents` (
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `word_count` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`manuscript_id`,`scene_id`),
  CONSTRAINT `fk_scene_content_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscript_versions` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `version_number` int NOT NULL,
  `label` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `snapshot_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `sections_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `metadata_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `parent_version_id` binary(16) DEFAULT NULL,
  `created_by` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `narrative_protected` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  KEY `idx_version_manuscript` (`manuscript_id`),
  KEY `idx_version_branch` (`branch_id`),
  KEY `fk_version_parent` (`parent_version_id`),
  KEY `fk_version_user` (`created_by`),
  CONSTRAINT `fk_version_branch` FOREIGN KEY (`branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_version_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_version_parent` FOREIGN KEY (`parent_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_version_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `manuscripts` (
  `id` binary(16) NOT NULL,
  `character_logs_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `sections_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `outline_id` binary(16) DEFAULT NULL,
  `current_branch_id` binary(16) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  `content_storage_version` int NOT NULL DEFAULT '1',
  `world_version_id` binary(16) DEFAULT NULL,
  `world_binding_revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKm1nvu0uvtf1nmbu4pgux6kdro` (`outline_id`),
  KEY `fk_manuscript_current_branch` (`current_branch_id`),
  KEY `idx_admin_manuscripts_page` (`updated_at`,`id`),
  KEY `fk_manuscript_world_version` (`world_version_id`),
  CONSTRAINT `fk_manuscript_current_branch` FOREIGN KEY (`current_branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_manuscript_outline` FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_manuscript_world_version` FOREIGN KEY (`world_version_id`) REFERENCES `world_versions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_auto_check_quota` (
  `owner_id` binary(16) NOT NULL,
  `quota_day` date NOT NULL,
  `used_count` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`owner_id`,`quota_day`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_basic_jobs` (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(24) NOT NULL DEFAULT 'QUEUED',
  `error_code` varchar(80) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`revision_id`),
  CONSTRAINT `fk_material_basic_revision` FOREIGN KEY (`revision_id`) REFERENCES `material_revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `material_chunks` (
  `chunk_id` varchar(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `material_id` binary(16) NOT NULL,
  `owner_user_id` binary(16) DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `text` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `chunk_seq` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`chunk_id`),
  KEY `idx_material_chunk_visibility` (`status`,`owner_user_id`,`material_id`),
  KEY `fk_material_chunk_owner` (`owner_user_id`),
  KEY `idx_material_chunk_version` (`material_id`,`content_version`),
  CONSTRAINT `fk_material_chunk_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_material_chunk_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_confirmed_annotations` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `candidate_index` int NOT NULL,
  `annotation_kind` varchar(16) NOT NULL,
  `name` varchar(255) NOT NULL,
  `candidate_json` longtext NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_material_confirmation` (`owner_id`,`task_id`,`candidate_index`),
  KEY `ix_material_confirmation_revision` (`revision_id`),
  KEY `fk_material_confirmation_task` (`task_id`),
  CONSTRAINT `fk_material_confirmation_revision` FOREIGN KEY (`revision_id`) REFERENCES `material_revisions` (`id`),
  CONSTRAINT `fk_material_confirmation_task` FOREIGN KEY (`task_id`) REFERENCES `material_processing_jobs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `material_duplicate_fingerprints` (
  `material_id` binary(16) NOT NULL,
  `content_version` bigint NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `terms_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`material_id`),
  CONSTRAINT `fk_duplicate_fingerprint_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_duplicate_jobs` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `execution_token` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comparisons` int NOT NULL DEFAULT '0',
  `incomplete` bit(1) NOT NULL DEFAULT b'0',
  `reason` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_duplicate_results` (
  `job_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_id` binary(16) NOT NULL,
  `target_id` binary(16) NOT NULL,
  `source_version` bigint NOT NULL,
  `target_version` bigint NOT NULL,
  `score` double NOT NULL,
  PRIMARY KEY (`job_id`,`source_id`,`target_id`),
  CONSTRAINT `fk_duplicate_result_job` FOREIGN KEY (`job_id`) REFERENCES `material_duplicate_jobs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_duplicate_terms` (
  `term` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `material_id` binary(16) NOT NULL,
  `content_version` bigint NOT NULL,
  PRIMARY KEY (`term`,`material_id`),
  KEY `fk_duplicate_term_material` (`material_id`),
  CONSTRAINT `fk_duplicate_term_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_entities` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_entity_names` (
  `entity_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`entity_id`,`name`),
  CONSTRAINT `material_entity_names_ibfk_1` FOREIGN KEY (`entity_id`) REFERENCES `material_entities` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_entity_sources` (
  `entity_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_id` binary(16) NOT NULL,
  `source_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`entity_id`,`material_id`),
  CONSTRAINT `material_entity_sources_ibfk_1` FOREIGN KEY (`entity_id`) REFERENCES `material_entities` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_evidence_chunks` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_id` binary(16) NOT NULL,
  `seq` int NOT NULL,
  `start_cp` int NOT NULL,
  `end_cp` int NOT NULL,
  `title` varchar(255) NOT NULL,
  `text` text NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_evidence_revision` (`revision_id`,`seq`),
  FULLTEXT KEY `ft_evidence_text` (`title`,`text`) /*!50100 WITH PARSER `ngram` */ ,
  CONSTRAINT `material_evidence_chunks_ibfk_1` FOREIGN KEY (`revision_id`) REFERENCES `material_revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `material_hint_slots` (
  `owner_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` varchar(100) NOT NULL,
  `cache_key` char(64) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `result_json` longtext,
  `observed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`owner_id`,`manuscript_id`,`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `material_index_jobs` (
  `id` binary(16) NOT NULL,
  `material_id` binary(16) NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '0',
  `next_attempt_at` datetime(6) DEFAULT NULL,
  `lease_owner` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_expires_at` datetime(6) DEFAULT NULL,
  `error_code` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_material_index_job_material` (`material_id`),
  KEY `idx_material_index_dispatch` (`status`,`next_attempt_at`,`lease_expires_at`),
  CONSTRAINT `fk_material_index_job_material` FOREIGN KEY (`material_id`) REFERENCES `materials` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_merge_sources` (
  `merged_revision` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `source_revision` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (`merged_revision`,`source_revision`),
  KEY `source_revision` (`source_revision`),
  CONSTRAINT `material_merge_sources_ibfk_1` FOREIGN KEY (`merged_revision`) REFERENCES `material_revisions` (`id`),
  CONSTRAINT `material_merge_sources_ibfk_2` FOREIGN KEY (`source_revision`) REFERENCES `material_revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_mutation_receipts` (
  `owner_id` binary(16) NOT NULL,
  `scope` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`owner_id`,`scope`,`request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_processing_jobs` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `kind` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_revision` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `manuscript_id` binary(16) DEFAULT NULL,
  `branch_id` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_version` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUEUED',
  `error_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_token` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_until` timestamp(6) NULL DEFAULT NULL,
  `call_limit` int NOT NULL,
  `calls_reserved` int NOT NULL DEFAULT '0',
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `provider_response_json` longtext COLLATE utf8mb4_unicode_ci,
  `gateway_user_id` bigint DEFAULT NULL,
  `evaluation_run_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `budget_waits` int NOT NULL DEFAULT '0',
  `not_before` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_material_job_intent` (`owner_id`,`kind`,`request_key`),
  KEY `ix_processing_dispatch` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_revisions` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `material_id` binary(16) NOT NULL,
  `content_version` bigint NOT NULL,
  `owner_id` binary(16) DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_material_revision` (`material_id`,`content_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_scene_packages` (
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `row_version` bigint NOT NULL DEFAULT '0',
  `pinned_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `excluded_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`manuscript_id`,`scene_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_semantic_jobs` (
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `profile` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `dimensions` int NOT NULL,
  `template_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `chunk_version` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'QUEUED',
  `error_code` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_token` char(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `lease_until` timestamp(6) NULL DEFAULT NULL,
  `request_owner_id` binary(16) DEFAULT NULL,
  `gateway_user_id` bigint DEFAULT NULL,
  `evaluation_run_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `budget_waits` int NOT NULL DEFAULT '0',
  `not_before` timestamp(6) NULL DEFAULT NULL,
  PRIMARY KEY (`revision_id`,`profile`),
  CONSTRAINT `material_semantic_jobs_ibfk_1` FOREIGN KEY (`revision_id`) REFERENCES `material_revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_source_links` (
  `id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `owner_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `branch_id` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `body_version` char(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision_id` char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `relation_type` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_cp` int NOT NULL,
  `end_cp` int NOT NULL,
  `quote` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `body_quote` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `state` varchar(24) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CURRENT',
  `request_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` char(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `body_block_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `body_start_cp` int DEFAULT NULL,
  `body_end_cp` int DEFAULT NULL,
  `body_conversion_version` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `report_task_id` char(36) CHARACTER SET ascii COLLATE ascii_bin DEFAULT NULL,
  `report_finding_index` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_source_link_intent` (`owner_id`,`request_key`),
  KEY `ix_source_links_manuscript` (`manuscript_id`),
  KEY `revision_id` (`revision_id`),
  KEY `ix_source_link_report` (`report_task_id`,`report_finding_index`),
  CONSTRAINT `fk_source_link_report` FOREIGN KEY (`report_task_id`) REFERENCES `material_processing_jobs` (`id`),
  CONSTRAINT `material_source_links_ibfk_1` FOREIGN KEY (`revision_id`) REFERENCES `material_revisions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_upload_jobs` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `progress` int NOT NULL,
  `result_material_id` binary(16) DEFAULT NULL,
  `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `owner_user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_upload_owner_id` (`owner_user_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_work_bindings` (
  `story_id` binary(16) NOT NULL,
  `material_id` binary(16) NOT NULL,
  PRIMARY KEY (`story_id`,`material_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `material_work_settings` (
  `story_id` binary(16) NOT NULL,
  `row_version` bigint NOT NULL DEFAULT '0',
  `semantic_profile` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'basic',
  `rerank_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `hints_enabled` tinyint(1) NOT NULL DEFAULT '0',
  `checks_enabled` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`story_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `materials` (
  `id` binary(16) NOT NULL,
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `entities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'LEGACY',
  `content_version` bigint NOT NULL DEFAULT '1',
  `row_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FK32wqk9p2efffrkb1l6yvkysou` (`user_id`),
  KEY `idx_material_status_id` (`status`,`created_at`,`id`),
  CONSTRAINT `FK32wqk9p2efffrkb1l6yvkysou` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `model_registry` (
  `id` binary(16) NOT NULL,
  `model_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `capabilities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `max_context_tokens` int NOT NULL DEFAULT '0',
  `max_output_tokens` int NOT NULL DEFAULT '0',
  `cost_per_1k_input` decimal(10,6) NOT NULL DEFAULT '0.000000',
  `cost_per_1k_output` decimal(10,6) NOT NULL DEFAULT '0.000000',
  `supports_streaming` bit(1) NOT NULL,
  `is_available` bit(1) NOT NULL,
  `priority` int NOT NULL DEFAULT '0',
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `gateway_model_id` bigint DEFAULT NULL,
  `thinking_json` longtext COLLATE utf8mb4_unicode_ci,
  `last_synced_at` timestamp(6) NULL DEFAULT NULL,
  `supports_image_input` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_registry_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `model_usage_logs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `model_id` binary(16) NOT NULL,
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_tokens` int NOT NULL DEFAULT '0',
  `output_tokens` int NOT NULL DEFAULT '0',
  `latency_ms` int NOT NULL DEFAULT '0',
  `cost_estimate` decimal(10,6) NOT NULL DEFAULT '0.000000',
  `success` bit(1) NOT NULL,
  `error_message` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_usage_user_created` (`user_id`,`created_at`),
  KEY `idx_usage_story` (`story_id`),
  KEY `fk_usage_model` (`model_id`),
  CONSTRAINT `fk_usage_model` FOREIGN KEY (`model_id`) REFERENCES `model_registry` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_usage_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_approvals` (
  `id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `version_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `confirmed_by` binary(16) NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `text_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `blocks_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `position_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `confirmed_at` datetime(6) NOT NULL,
  `text_conversion_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_narrative_approval_request` (`branch_id`,`idempotency_key`),
  KEY `idx_narrative_approval_scene` (`branch_id`,`scene_id`),
  KEY `fk_narrative_approval_version` (`version_id`),
  CONSTRAINT `fk_narrative_approval_ledger` FOREIGN KEY (`branch_id`) REFERENCES `narrative_ledgers` (`branch_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_narrative_approval_version` FOREIGN KEY (`version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_commits` (
  `id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `revision` bigint NOT NULL,
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `author_id` binary(16) DEFAULT NULL,
  `detail_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_narrative_commit_revision` (`branch_id`,`revision`),
  UNIQUE KEY `uk_narrative_commit_request` (`branch_id`,`idempotency_key`),
  CONSTRAINT `fk_narrative_commit_ledger` FOREIGN KEY (`branch_id`) REFERENCES `narrative_ledgers` (`branch_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_context_revisions` (
  `id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `revision` bigint NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `receipt_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL,
  `author_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_context_revision` (`branch_id`,`revision`),
  UNIQUE KEY `uq_context_idempotency` (`branch_id`,`idempotency_key`),
  CONSTRAINT `fk_context_revision_branch` FOREIGN KEY (`branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_context_settings` (
  `story_id` binary(16) NOT NULL,
  `enabled` bit(1) NOT NULL DEFAULT b'0',
  `revision` bigint NOT NULL DEFAULT '0',
  `lock_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`story_id`),
  CONSTRAINT `fk_context_settings_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_extractions` (
  `id` binary(16) NOT NULL,
  `approval_id` binary(16) NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `base_canon_revision` bigint NOT NULL,
  `prompt_version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `model` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `input_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `candidates_json` longtext COLLATE utf8mb4_unicode_ci,
  `usage_json` longtext COLLATE utf8mb4_unicode_ci,
  `review_json` longtext COLLATE utf8mb4_unicode_ci,
  `error` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `text_conversion_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_narrative_extraction_approval` (`approval_id`),
  CONSTRAINT `fk_narrative_extraction_approval` FOREIGN KEY (`approval_id`) REFERENCES `narrative_approvals` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_generation_candidates` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `stamp_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `generation_sequence` int DEFAULT NULL,
  `manuscript_version` bigint DEFAULT NULL,
  `min_han` int DEFAULT NULL,
  `max_han` int DEFAULT NULL,
  `actual_han` int DEFAULT NULL,
  `quality_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_kind` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `metadata_json` longtext COLLATE utf8mb4_unicode_ci,
  `request_json` longtext COLLATE utf8mb4_unicode_ci,
  `adopted_version` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_scene_candidate_operation_sequence` (`operation_id`,`generation_sequence`),
  KEY `fk_context_candidate_branch` (`branch_id`),
  KEY `idx_scene_candidates` (`manuscript_id`,`branch_id`,`scene_id`,`created_at`),
  CONSTRAINT `fk_context_candidate_branch` FOREIGN KEY (`branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_context_candidate_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_ledgers` (
  `branch_id` binary(16) NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `lock_version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`branch_id`),
  CONSTRAINT `fk_narrative_ledger_branch` FOREIGN KEY (`branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `narrative_records` (
  `id` binary(16) NOT NULL,
  `extraction_id` binary(16) NOT NULL,
  `assertion_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `dependency_ids_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_revision` bigint NOT NULL,
  `invalidated_revision` bigint DEFAULT NULL,
  `superseded_revision` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_narrative_record_extraction` (`extraction_id`),
  CONSTRAINT `fk_narrative_record_extraction` FOREIGN KEY (`extraction_id`) REFERENCES `narrative_extractions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `outline_ai_results` (
  `id` binary(16) NOT NULL,
  `outline_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `frozen_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `raw_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `candidate_json` longtext COLLATE utf8mb4_unicode_ci,
  `receipt_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_outline_ai_request` (`outline_id`,`request_key`),
  UNIQUE KEY `uk_outline_ai_operation` (`operation_id`),
  KEY `idx_outline_ai_recent` (`outline_id`,`created_at`,`id`),
  CONSTRAINT `fk_outline_ai_outline` FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `outlines` (
  `id` binary(16) NOT NULL,
  `content_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `world_version_id` binary(16) DEFAULT NULL,
  `world_binding_revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKij56qu3qp6w5ykg4cxauyo1h7` (`story_id`),
  KEY `fk_outline_world_version` (`world_version_id`),
  CONSTRAINT `fk_outline_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_outline_world_version` FOREIGN KEY (`world_version_id`) REFERENCES `world_versions` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `plot_quality_issues` (
  `id` binary(16) NOT NULL,
  `run_id` binary(16) NOT NULL,
  `dimension` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_score` int NOT NULL,
  `evidence` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `why_it_matters` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `minimal_fix` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_plot_issue_run` (`run_id`),
  KEY `idx_plot_issue_dimension` (`dimension`),
  CONSTRAINT `fk_plot_issue_run` FOREIGN KEY (`run_id`) REFERENCES `plot_quality_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `plot_quality_runs` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `chapter_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scene_title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `chapter_order` int NOT NULL DEFAULT '0',
  `scene_order` int NOT NULL DEFAULT '0',
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_severity` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `overall_risk_score` int NOT NULL,
  `source_text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rewrite_plan_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `surgical_fixes_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `revision_candidate_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `revision_applied` bit(1) NOT NULL,
  `revision_applied_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `isolation_stamp_json` longtext COLLATE utf8mb4_unicode_ci,
  `isolation_context` longtext COLLATE utf8mb4_unicode_ci,
  `text_conversion_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_plot_run_story` (`story_id`),
  KEY `idx_plot_run_manuscript` (`manuscript_id`),
  KEY `idx_plot_run_scene` (`scene_id`),
  KEY `idx_admin_plot_page` (`created_at`,`id`),
  CONSTRAINT `fk_plot_run_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_plot_run_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_credit_accounts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `balance` bigint NOT NULL,
  `version` bigint NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_credit_account_user` (`user_id`),
  CONSTRAINT `fk_project_credit_account_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_credit_ledger` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `entry_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `delta` bigint NOT NULL,
  `balance_after` bigint NOT NULL,
  `reference_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_credit_ledger_user_idempotency` (`user_id`,`idempotency_key`),
  KEY `idx_project_credit_ledger_user_created` (`user_id`,`created_at`),
  KEY `idx_project_credit_ledger_reference` (`reference_type`,`reference_id`,`entry_type`),
  CONSTRAINT `fk_project_credit_ledger_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `prompt_templates` (
  `id` binary(16) NOT NULL,
  `manuscript_section` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `outline_chapter` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `refine_with_instruction` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `refine_without_instruction` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `story_creation` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8dm9s6fl88ua6j9y6w90hun67` (`user_id`),
  CONSTRAINT `FK49ya7kke8ffdsxx5djymew73k` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `redeem_code_usages` (
  `id` binary(16) NOT NULL,
  `redeem_code_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redeem_code_user` (`redeem_code_id`,`user_id`),
  KEY `idx_redeem_usage_user` (`user_id`),
  CONSTRAINT `fk_redeem_usage_code` FOREIGN KEY (`redeem_code_id`) REFERENCES `redeem_codes` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_redeem_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `redeem_codes` (
  `id` binary(16) NOT NULL,
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `grant_amount` bigint NOT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL,
  `starts_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `stackable` bit(1) NOT NULL,
  `description` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redeem_code_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `scene_generation_runs` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `generation_version_id` binary(16) NOT NULL,
  `created_by` binary(16) NOT NULL,
  `previous_run_id` binary(16) DEFAULT NULL,
  `superseded_by_run_id` binary(16) DEFAULT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attribution_status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `mode` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `model_key` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `prompt_version` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `attempt_count` int NOT NULL DEFAULT '1',
  `context_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `prompt_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `context_manifest_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `generated_content_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_content_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `generated_characters` int NOT NULL,
  `current_characters` int DEFAULT NULL,
  `retained_characters` int DEFAULT NULL,
  `added_characters` int DEFAULT NULL,
  `deleted_characters` int DEFAULT NULL,
  `retention_rate` decimal(8,6) DEFAULT NULL,
  `exact_match` bit(1) DEFAULT NULL,
  `diff_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `tags_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `note` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `preference_confirmed` bit(1) NOT NULL DEFAULT b'0',
  `preference_confirmed_at` datetime(6) DEFAULT NULL,
  `first_edited_at` datetime(6) DEFAULT NULL,
  `last_edited_at` datetime(6) DEFAULT NULL,
  `recomputed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `model_selection_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_scene_generation_run_scene` (`manuscript_id`,`scene_id`,`created_at`),
  KEY `idx_scene_generation_run_active` (`manuscript_id`,`scene_id`,`status`),
  KEY `idx_scene_generation_run_version` (`generation_version_id`),
  KEY `idx_scene_generation_run_created_by` (`created_by`),
  KEY `idx_scene_generation_run_previous` (`previous_run_id`),
  KEY `idx_scene_generation_run_superseded_by` (`superseded_by_run_id`),
  CONSTRAINT `fk_scene_generation_run_created_by` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_scene_generation_run_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_scene_generation_run_previous` FOREIGN KEY (`previous_run_id`) REFERENCES `scene_generation_runs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_scene_generation_run_superseded_by` FOREIGN KEY (`superseded_by_run_id`) REFERENCES `scene_generation_runs` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_scene_generation_run_version` FOREIGN KEY (`generation_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `slop_drift_runs` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `overall_risk_score` int NOT NULL,
  `risk_label` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `safe_claim` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_characters` int NOT NULL,
  `window_count` int NOT NULL,
  `source_text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `window_summaries_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `metric_curves_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `drift_points_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `evidence_items_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `alternative_explanations_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `rewrite_tasks_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `text_conversion_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_slop_drift_story` (`story_id`),
  KEY `idx_slop_drift_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_slop_drift_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_slop_drift_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `slop_patterns` (
  `id` binary(16) NOT NULL,
  `category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'PHRASE | BODY_ACTION | IMAGERY | ENDING_CLICHE | NARRATIVE_MECHANIC',
  `pattern` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '供 prompt 注入的禁用表达描述，面向模型可理解的自然语言',
  `weight` tinyint NOT NULL DEFAULT '2' COMMENT '1-5, 越高越优先被采样',
  `enabled` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  KEY `idx_slop_patterns_category` (`category`),
  KEY `idx_slop_patterns_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `slop_quality_issues` (
  `id` binary(16) NOT NULL,
  `run_id` binary(16) NOT NULL,
  `dimension` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `risk_score` int NOT NULL,
  `evidence` varchar(600) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `why_it_matters` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `minimal_fix` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `char_start` int DEFAULT NULL,
  `char_end` int DEFAULT NULL,
  `quote` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `pattern_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issue_type` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `alternative_explanations_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `repair_hint` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_slop_issue_run` (`run_id`),
  KEY `idx_slop_issue_dimension` (`dimension`),
  CONSTRAINT `fk_slop_issue_run` FOREIGN KEY (`run_id`) REFERENCES `slop_quality_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `slop_quality_runs` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_severity` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `overall_risk_score` int NOT NULL,
  `revised` bit(1) NOT NULL,
  `revision_count` int NOT NULL DEFAULT '0',
  `candidate_text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `accepted_text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_text_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `analysis_mode` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_label` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_level` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `safe_claim` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `module_scores_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `alternative_explanations_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `revision_priorities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `rewrite_tasks_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `summary` varchar(800) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `text_conversion_version` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `review_context_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_slop_run_story` (`story_id`),
  KEY `idx_slop_run_manuscript` (`manuscript_id`),
  KEY `idx_slop_run_scene` (`scene_id`),
  KEY `idx_admin_slop_page` (`created_at`,`id`),
  CONSTRAINT `fk_slop_run_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_slop_run_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `stories` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `genre` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `synopsis` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tone` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `world_version_id` binary(16) DEFAULT NULL,
  `world_binding_revision` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `FKshv2ytgbsn9w9mpu43mc6ln6j` (`user_id`),
  KEY `idx_admin_stories_page` (`updated_at`,`id`),
  KEY `fk_story_world_version` (`world_version_id`),
  CONSTRAINT `fk_story_world_version` FOREIGN KEY (`world_version_id`) REFERENCES `world_versions` (`id`),
  CONSTRAINT `FKshv2ytgbsn9w9mpu43mc6ln6j` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `story_assistant_proposals` (
  `id` binary(16) NOT NULL,
  `turn_id` binary(16) NOT NULL,
  `item_index` int NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `data_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_assistant_item` (`turn_id`,`item_index`),
  CONSTRAINT `fk_story_assistant_proposal_turn` FOREIGN KEY (`turn_id`) REFERENCES `story_assistant_turns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `story_assistant_receipts` (
  `story_id` binary(16) NOT NULL,
  `scope_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`story_id`,`scope_key`,`request_key`),
  CONSTRAINT `fk_story_assistant_receipt_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `story_assistant_sessions` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_story_assistant_recent` (`story_id`,`user_id`,`updated_at`,`id`),
  CONSTRAINT `fk_story_assistant_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `story_assistant_turns` (
  `id` binary(16) NOT NULL,
  `session_id` binary(16) NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `frozen_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `plan_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `reads_json` longtext COLLATE utf8mb4_unicode_ci,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_assistant_turn_request` (`session_id`,`request_key`),
  UNIQUE KEY `uk_story_assistant_turn_operation` (`operation_id`),
  KEY `idx_story_assistant_turn_history` (`session_id`,`created_at`,`id`),
  CONSTRAINT `fk_story_assistant_turn_session` FOREIGN KEY (`session_id`) REFERENCES `story_assistant_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `story_world_eras` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `reviewed_version_id` binary(16) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `era_ids_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_story_world_eras` (`story_id`,`world_id`),
  KEY `fk_story_world_eras_world` (`world_id`),
  KEY `fk_story_world_eras_version` (`reviewed_version_id`),
  CONSTRAINT `fk_story_world_eras_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_story_world_eras_version` FOREIGN KEY (`reviewed_version_id`) REFERENCES `world_versions` (`id`),
  CONSTRAINT `fk_story_world_eras_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `style_analysis_jobs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `source_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_style_analysis_user` (`user_id`),
  CONSTRAINT `fk_style_analysis_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `style_profile_scene_overrides` (
  `id` binary(16) NOT NULL,
  `style_profile_id` binary(16) NOT NULL,
  `scene_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `override_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_style_profile_scene` (`style_profile_id`,`scene_type`),
  CONSTRAINT `fk_style_override_profile` FOREIGN KEY (`style_profile_id`) REFERENCES `style_profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `style_profiles` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dimensions_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `sample_text` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `ai_analysis_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_style_profile_user` (`user_id`),
  KEY `idx_style_profile_story` (`story_id`),
  CONSTRAINT `fk_style_profile_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_style_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `task_model_routing` (
  `id` binary(16) NOT NULL,
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `recommended_model_id` binary(16) NOT NULL,
  `fallback_model_id` binary(16) DEFAULT NULL,
  `routing_strategy` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `billing_enabled` bit(1) NOT NULL DEFAULT b'1',
  `configuration_revision` bigint NOT NULL DEFAULT '0',
  `default_option_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_model_routing_task` (`task_type`),
  KEY `fk_task_routing_recommended` (`recommended_model_id`),
  KEY `fk_task_routing_fallback` (`fallback_model_id`),
  CONSTRAINT `fk_task_routing_fallback` FOREIGN KEY (`fallback_model_id`) REFERENCES `model_registry` (`id`),
  CONSTRAINT `fk_task_routing_recommended` FOREIGN KEY (`recommended_model_id`) REFERENCES `model_registry` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_model_preferences` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `task_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `preferred_model_id` binary(16) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `preferred_option_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_model_pref` (`user_id`,`task_type`),
  KEY `fk_user_pref_model` (`preferred_model_id`),
  CONSTRAINT `fk_user_pref_model` FOREIGN KEY (`preferred_model_id`) REFERENCES `model_registry` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_user_pref_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_roles` (
  `user_id` binary(16) NOT NULL,
  `role` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `users` (
  `id` binary(16) NOT NULL,
  `avatar_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banned` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credits` double NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_uid` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UKl6igvmj08ovhtjfdxf9cm493c` (`remote_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `version_diffs` (
  `id` binary(16) NOT NULL,
  `from_version_id` binary(16) NOT NULL,
  `to_version_id` binary(16) NOT NULL,
  `diff_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_diff_pair` (`from_version_id`,`to_version_id`),
  KEY `fk_diff_to` (`to_version_id`),
  CONSTRAINT `fk_diff_from` FOREIGN KEY (`from_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_diff_to` FOREIGN KEY (`to_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `workspace_layouts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `layout_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_layout_user` (`user_id`),
  CONSTRAINT `fk_layout_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_assistant_proposals` (
  `id` binary(16) NOT NULL,
  `turn_id` binary(16) NOT NULL,
  `item_index` int NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resource_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `data_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_world_assistant_item` (`turn_id`,`item_index`),
  CONSTRAINT `fk_world_assistant_proposal_turn` FOREIGN KEY (`turn_id`) REFERENCES `world_assistant_turns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_assistant_receipts` (
  `world_id` binary(16) NOT NULL,
  `scope_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`world_id`,`scope_key`,`request_key`),
  CONSTRAINT `fk_world_assistant_receipt_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_assistant_sessions` (
  `id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `title` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  `updated_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_world_assistant_recent` (`world_id`,`user_id`,`updated_at`,`id`),
  CONSTRAINT `fk_world_assistant_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_assistant_turns` (
  `id` binary(16) NOT NULL,
  `session_id` binary(16) NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `frozen_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `plan_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `reads_json` longtext COLLATE utf8mb4_unicode_ci,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_world_assistant_turn_request` (`session_id`,`request_key`),
  UNIQUE KEY `uk_world_assistant_turn_operation` (`operation_id`),
  KEY `idx_world_assistant_turn_history` (`session_id`,`created_at`,`id`),
  CONSTRAINT `fk_world_assistant_turn_session` FOREIGN KEY (`session_id`) REFERENCES `world_assistant_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_context_preferences` (
  `scope_key` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `world_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `reviewed_version_id` binary(16) NOT NULL,
  `choices_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `reviewed_hashes_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`scope_key`,`world_id`),
  KEY `fk_world_context_world` (`world_id`),
  KEY `fk_world_context_version` (`reviewed_version_id`),
  CONSTRAINT `fk_world_context_version` FOREIGN KEY (`reviewed_version_id`) REFERENCES `world_versions` (`id`),
  CONSTRAINT `fk_world_context_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_copy_receipts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `request_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_world_id` binary(16) NOT NULL,
  `copied_world_id` binary(16) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_world_copy_request` (`user_id`,`request_key`),
  CONSTRAINT `fk_world_copy_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_entries` (
  `id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` longtext COLLATE utf8mb4_unicode_ci,
  `content` longtext COLLATE utf8mb4_unicode_ci,
  `aliases_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `legacy_module_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `legacy_field_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_world_entry_origin` (`world_id`,`legacy_module_key`,`legacy_field_key`),
  KEY `idx_world_entry_world` (`world_id`),
  CONSTRAINT `fk_world_entry_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_prompt_templates` (
  `id` binary(16) NOT NULL,
  `field_refine` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `final_templates_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `modules_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `action_templates_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjyf0gnbhdrdp109akcggleif3` (`user_id`),
  CONSTRAINT `FK2tiidjlqeb20m1ivkw30u8diu` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_publish_receipts` (
  `id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `version_id` binary(16) NOT NULL,
  `request_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_world_publish_request` (`world_id`,`request_key`),
  KEY `fk_world_publish_version` (`version_id`),
  CONSTRAINT `fk_world_publish_version` FOREIGN KEY (`version_id`) REFERENCES `world_versions` (`id`),
  CONSTRAINT `fk_world_publish_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_relations` (
  `id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `source_id` binary(16) NOT NULL,
  `target_id` binary(16) NOT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `fk_world_relation_source` (`source_id`),
  KEY `fk_world_relation_target` (`target_id`),
  KEY `idx_world_relation_world` (`world_id`),
  CONSTRAINT `fk_world_relation_source` FOREIGN KEY (`source_id`) REFERENCES `world_entries` (`id`),
  CONSTRAINT `fk_world_relation_target` FOREIGN KEY (`target_id`) REFERENCES `world_entries` (`id`),
  CONSTRAINT `fk_world_relation_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `world_versions` (
  `id` binary(16) NOT NULL,
  `world_id` binary(16) NOT NULL,
  `sequence_number` int NOT NULL,
  `origin` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `legacy_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `draft_revision` bigint NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `snapshot_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `release_note` longtext COLLATE utf8mb4_unicode_ci,
  `request_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_world_version_sequence` (`world_id`,`sequence_number`),
  UNIQUE KEY `uq_world_version_request` (`world_id`,`request_key`),
  CONSTRAINT `fk_world_version_world` FOREIGN KEY (`world_id`) REFERENCES `worlds` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `worlds` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `creative_intent` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `module_progress_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `modules_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `status` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tagline` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `themes_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `legacy_modules_json` longtext COLLATE utf8mb4_unicode_ci,
  `draft_content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `overview` longtext COLLATE utf8mb4_unicode_ci,
  `timeline_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `FK9mp0grigxj1tudlm6p9e6gxw0` (`user_id`),
  KEY `idx_admin_worlds_page` (`updated_at`,`id`),
  CONSTRAINT `FK9mp0grigxj1tudlm6p9e6gxw0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_agent_runs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `revision` bigint NOT NULL DEFAULT '0',
  `operation_id` binary(16) DEFAULT NULL,
  `frozen_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `state_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `max_cost_cny` decimal(18,6) NOT NULL,
  `max_project_credits` bigint NOT NULL,
  `decision_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_writing_agent_request` (`user_id`,`manuscript_id`,`scene_id`,`request_key`),
  KEY `fk_writing_agent_manuscript` (`manuscript_id`),
  KEY `idx_writing_agent_recent` (`user_id`,`manuscript_id`,`scene_id`,`created_at`,`id`),
  CONSTRAINT `fk_writing_agent_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_agent_steps` (
  `id` binary(16) NOT NULL,
  `run_id` binary(16) NOT NULL,
  `sequence_number` int NOT NULL,
  `call_key` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `kind` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `feature_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `gateway_request_base64` longtext COLLATE utf8mb4_unicode_ci,
  `budget_json` longtext COLLATE utf8mb4_unicode_ci,
  `gateway_result_base64` longtext COLLATE utf8mb4_unicode_ci,
  `settlement_state` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reserved_cny` decimal(18,6) NOT NULL DEFAULT '0.000000',
  `settled_cny` decimal(18,6) DEFAULT NULL,
  `reserved_project_credits` bigint NOT NULL DEFAULT '0',
  `settled_project_credits` bigint DEFAULT NULL,
  `created_at` timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_writing_agent_step` (`run_id`,`sequence_number`),
  UNIQUE KEY `uk_writing_agent_call` (`run_id`,`call_key`),
  CONSTRAINT `fk_writing_agent_step` FOREIGN KEY (`run_id`) REFERENCES `writing_agent_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_assistant_proposals` (
  `id` binary(16) NOT NULL,
  `turn_id` binary(16) NOT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `data_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `receipts_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_proposal_turn` (`turn_id`),
  CONSTRAINT `fk_assistant_proposal_turn` FOREIGN KEY (`turn_id`) REFERENCES `writing_assistant_turns` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_assistant_sessions` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `scene_id` binary(16) NOT NULL,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_session` (`user_id`,`manuscript_id`,`branch_id`,`scene_id`),
  KEY `fk_assistant_session_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_assistant_session_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_assistant_turns` (
  `id` binary(16) NOT NULL,
  `session_id` binary(16) NOT NULL,
  `request_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_id` binary(16) DEFAULT NULL,
  `frozen_json` longtext COLLATE utf8mb4_unicode_ci NOT NULL,
  `plan_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `plan_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_request_json` longtext COLLATE utf8mb4_unicode_ci,
  `final_result_json` longtext COLLATE utf8mb4_unicode_ci,
  `reads_json` longtext COLLATE utf8mb4_unicode_ci,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` timestamp(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_assistant_turn_request` (`session_id`,`request_key`),
  UNIQUE KEY `uk_assistant_turn_operation` (`operation_id`),
  KEY `idx_assistant_turn_history` (`session_id`,`created_at`,`id`),
  CONSTRAINT `fk_assistant_turn_session` FOREIGN KEY (`session_id`) REFERENCES `writing_assistant_sessions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_goals` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `goal_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_value` int NOT NULL,
  `current_value` int NOT NULL DEFAULT '0',
  `deadline` date DEFAULT NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_goal_user` (`user_id`),
  KEY `fk_goal_story` (`story_id`),
  CONSTRAINT `fk_goal_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_goal_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `writing_sessions` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `words_written` int NOT NULL DEFAULT '0',
  `words_deleted` int NOT NULL DEFAULT '0',
  `net_words` int NOT NULL DEFAULT '0',
  `duration_seconds` int NOT NULL DEFAULT '0',
  `chapters_edited_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_session_user` (`user_id`),
  KEY `idx_session_story` (`story_id`),
  CONSTRAINT `fk_session_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

