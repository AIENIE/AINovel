CREATE TABLE `ai_operation_runs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `operation_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_type` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `scope_id` binary(16) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_step` varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total_steps` int NOT NULL DEFAULT 1,
  `completed_steps` int NOT NULL DEFAULT 0,
  `output_tokens` bigint NOT NULL DEFAULT 0,
  `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1',
  `attempt_count` int NOT NULL DEFAULT 0,
  `stream_started` bit(1) NOT NULL DEFAULT b'0',
  `request_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `model_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` longtext COLLATE utf8mb4_unicode_ci,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_operation_user_updated` (`user_id`,`updated_at`),
  KEY `idx_ai_operation_scope_status` (`scope_type`,`scope_id`,`status`),
  KEY `idx_ai_operation_status_updated` (`status`,`updated_at`),
  CONSTRAINT `fk_ai_operation_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `async_jobs`
  ADD COLUMN `output_tokens` bigint NOT NULL DEFAULT 0 AFTER `progress`,
  ADD COLUMN `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1' AFTER `output_tokens`;

CREATE TABLE `ai_operation_steps` (
  `id` binary(16) NOT NULL,
  `operation_id` binary(16) NOT NULL,
  `step_index` int NOT NULL,
  `step_key` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `step_label` varchar(160) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `output_tokens` bigint NOT NULL DEFAULT 0,
  `output_tokens_estimated` bit(1) NOT NULL DEFAULT b'1',
  `prompt_tokens` bigint NOT NULL DEFAULT 0,
  `completion_tokens` bigint NOT NULL DEFAULT 0,
  `cache_tokens` bigint NOT NULL DEFAULT 0,
  `request_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ai_operation_step` (`operation_id`,`step_index`),
  CONSTRAINT `fk_ai_operation_step_run` FOREIGN KEY (`operation_id`) REFERENCES `ai_operation_runs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
