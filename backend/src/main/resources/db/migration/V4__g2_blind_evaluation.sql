-- AINovel V4: durable G2 Step 1 blind-evaluation campaigns and refundable AI samples.

-- Some legacy databases were baselined before the local project-credit tables were
-- introduced. G2 generation and failure refunds require both tables to exist.
CREATE TABLE IF NOT EXISTS `project_credit_accounts` (
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

CREATE TABLE IF NOT EXISTS `project_credit_ledger` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `entry_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `delta` bigint NOT NULL,
  `balance_after` bigint NOT NULL,
  `reference_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_project_credit_ledger_user_created` (`user_id`,`created_at`),
  CONSTRAINT `fk_project_credit_ledger_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_experiments` (
  `id` binary(16) NOT NULL,
  `title` varchar(180) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by` binary(16) NOT NULL,
  `minimum_votes` int NOT NULL DEFAULT 100,
  `minimum_sample_pairs` int NOT NULL DEFAULT 20,
  `minimum_reviewers` int NOT NULL DEFAULT 10,
  `crafted_win_rate_target` decimal(5,2) NOT NULL DEFAULT 55.00,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_g2_experiment_status` (`status`),
  CONSTRAINT `fk_g2_experiment_creator` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_invites` (
  `id` binary(16) NOT NULL,
  `experiment_id` binary(16) NOT NULL,
  `reviewer_id` binary(16) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
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
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `fast_text` mediumtext COLLATE utf8mb4_unicode_ci,
  `crafted_text` mediumtext COLLATE utf8mb4_unicode_ci,
  `failure_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `refunded_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_g2_sample_source` (`experiment_id`,`author_id`,`manuscript_id`,`scene_id`),
  KEY `idx_g2_sample_experiment_status` (`experiment_id`,`status`),
  KEY `idx_g2_sample_author` (`author_id`),
  CONSTRAINT `fk_g2_sample_experiment` FOREIGN KEY (`experiment_id`) REFERENCES `g2_evaluation_experiments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_sample_author` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_sample_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `g2_evaluation_votes` (
  `id` binary(16) NOT NULL,
  `experiment_id` binary(16) NOT NULL,
  `sample_id` binary(16) NOT NULL,
  `reviewer_id` binary(16) NOT NULL,
  `choice` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_g2_vote_sample_reviewer` (`sample_id`,`reviewer_id`),
  KEY `idx_g2_vote_experiment` (`experiment_id`,`created_at`),
  KEY `idx_g2_vote_reviewer` (`reviewer_id`),
  CONSTRAINT `fk_g2_vote_experiment` FOREIGN KEY (`experiment_id`) REFERENCES `g2_evaluation_experiments` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_vote_sample` FOREIGN KEY (`sample_id`) REFERENCES `g2_evaluation_samples` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_g2_vote_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @g2_credit_reference_index_exists = (
  SELECT COUNT(*)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'project_credit_ledger'
    AND index_name = 'idx_project_credit_ledger_reference'
);
SET @g2_credit_reference_index_sql = IF(
  @g2_credit_reference_index_exists = 0,
  'CREATE INDEX `idx_project_credit_ledger_reference` ON `project_credit_ledger` (`reference_type`,`reference_id`,`entry_type`)',
  'SELECT 1'
);
PREPARE g2_credit_reference_index_statement FROM @g2_credit_reference_index_sql;
EXECUTE g2_credit_reference_index_statement;
DEALLOCATE PREPARE g2_credit_reference_index_statement;
