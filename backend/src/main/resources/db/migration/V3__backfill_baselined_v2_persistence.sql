-- AINovel V3: restore v2 persistence missing from pre-Flyway baselined databases.
--
-- The V1 baseline was derived from a later, complete schema. Some legacy databases
-- were baselined at V1 before these v2 tables existed, so Flyway correctly skipped
-- V1 while the runtime was still missing active persistence tables. This migration
-- is additive and safe for databases created from the complete V1 baseline.

SET @v3_sql_notes = @@SESSION.sql_notes;
SET sql_notes = 0;
SET @v3_schema = DATABASE();
SET @v3_has_current_branch_id = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = @v3_schema
    AND table_name = 'manuscripts'
    AND column_name = 'current_branch_id'
);
SET @v3_sql = IF(
  @v3_has_current_branch_id = 0,
  'ALTER TABLE `manuscripts` ADD COLUMN `current_branch_id` binary(16) DEFAULT NULL',
  'SELECT 1'
);
PREPARE v3_statement FROM @v3_sql;
EXECUTE v3_statement;
DEALLOCATE PREPARE v3_statement;

CREATE TABLE IF NOT EXISTS `lorebook_entries` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `entry_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci,
  `keywords_json` longtext COLLATE utf8mb4_unicode_ci,
  `priority` int NOT NULL DEFAULT 0,
  `enabled` bit(1) NOT NULL,
  `insertion_position` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_budget` int NOT NULL DEFAULT 500,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_lorebook_story` (`story_id`),
  KEY `idx_lorebook_user` (`user_id`),
  CONSTRAINT `fk_lorebook_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_lorebook_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `entity_extractions` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) DEFAULT NULL,
  `entity_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `entity_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attributes_json` longtext COLLATE utf8mb4_unicode_ci,
  `source_text` longtext COLLATE utf8mb4_unicode_ci,
  `confidence` double NOT NULL DEFAULT 0,
  `reviewed` bit(1) NOT NULL,
  `review_action` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linked_lorebook_id` binary(16) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_extraction_story` (`story_id`),
  KEY `idx_extraction_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_extraction_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_extraction_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_extraction_lorebook` FOREIGN KEY (`linked_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `knowledge_graph_relationships` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `source_lorebook_id` binary(16) NOT NULL,
  `target_lorebook_id` binary(16) NOT NULL,
  `relation_type` varchar(80) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_kg_story` (`story_id`),
  KEY `idx_kg_source` (`source_lorebook_id`),
  KEY `idx_kg_target` (`target_lorebook_id`),
  CONSTRAINT `fk_kg_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kg_source` FOREIGN KEY (`source_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_kg_target` FOREIGN KEY (`target_lorebook_id`) REFERENCES `lorebook_entries` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `style_profiles` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `profile_type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `dimensions_json` longtext COLLATE utf8mb4_unicode_ci,
  `sample_text` longtext COLLATE utf8mb4_unicode_ci,
  `ai_analysis_json` longtext COLLATE utf8mb4_unicode_ci,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_style_profile_user` (`user_id`),
  KEY `idx_style_profile_story` (`story_id`),
  CONSTRAINT `fk_style_profile_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_style_profile_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `style_profile_scene_overrides` (
  `id` binary(16) NOT NULL,
  `style_profile_id` binary(16) NOT NULL,
  `scene_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `override_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_style_profile_scene` (`style_profile_id`,`scene_type`),
  CONSTRAINT `fk_style_override_profile` FOREIGN KEY (`style_profile_id`) REFERENCES `style_profiles` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `character_voices` (
  `id` binary(16) NOT NULL,
  `character_card_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `speech_pattern` longtext COLLATE utf8mb4_unicode_ci,
  `vocabulary_level` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `catchphrases_json` longtext COLLATE utf8mb4_unicode_ci,
  `emotional_range_json` longtext COLLATE utf8mb4_unicode_ci,
  `dialect` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sample_dialogues_json` longtext COLLATE utf8mb4_unicode_ci,
  `ai_analysis_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_character_voice_character` (`character_card_id`),
  KEY `idx_character_voice_story` (`story_id`),
  CONSTRAINT `fk_character_voice_character` FOREIGN KEY (`character_card_id`) REFERENCES `character_cards` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_character_voice_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `style_analysis_jobs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_reference` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `result_json` longtext COLLATE utf8mb4_unicode_ci,
  `error_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_style_analysis_user` (`user_id`),
  CONSTRAINT `fk_style_analysis_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `beta_reader_reports` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_reference` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `analysis_json` longtext COLLATE utf8mb4_unicode_ci,
  `summary` longtext COLLATE utf8mb4_unicode_ci,
  `score_overall` int DEFAULT NULL,
  `score_pacing` int DEFAULT NULL,
  `score_characters` int DEFAULT NULL,
  `score_dialogue` int DEFAULT NULL,
  `score_consistency` int DEFAULT NULL,
  `score_engagement` int DEFAULT NULL,
  `token_cost` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_beta_story` (`story_id`),
  KEY `idx_beta_user` (`user_id`),
  CONSTRAINT `fk_beta_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_beta_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `continuity_issues` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `report_id` binary(16) DEFAULT NULL,
  `issue_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `severity` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `evidence_json` longtext COLLATE utf8mb4_unicode_ci,
  `suggestion` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_continuity_story` (`story_id`),
  KEY `idx_continuity_report` (`report_id`),
  CONSTRAINT `fk_continuity_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_continuity_report` FOREIGN KEY (`report_id`) REFERENCES `beta_reader_reports` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `analysis_jobs` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `job_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `scope_reference` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT 0,
  `progress_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result_reference` binary(16) DEFAULT NULL,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_analysis_job_story` (`story_id`),
  KEY `idx_analysis_job_user` (`user_id`),
  CONSTRAINT `fk_analysis_job_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_analysis_job_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `manuscript_branches` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` longtext COLLATE utf8mb4_unicode_ci,
  `source_version_id` binary(16) DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_main` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_branch_manuscript_name` (`manuscript_id`,`name`),
  KEY `idx_branch_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_branch_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `manuscript_versions` (
  `id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `branch_id` binary(16) NOT NULL,
  `version_number` int NOT NULL,
  `label` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `snapshot_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sections_json` longtext COLLATE utf8mb4_unicode_ci,
  `metadata_json` longtext COLLATE utf8mb4_unicode_ci,
  `parent_version_id` binary(16) DEFAULT NULL,
  `created_by` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_version_manuscript` (`manuscript_id`),
  KEY `idx_version_branch` (`branch_id`),
  CONSTRAINT `fk_version_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_version_branch` FOREIGN KEY (`branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_version_parent` FOREIGN KEY (`parent_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_version_user` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `version_diffs` (
  `id` binary(16) NOT NULL,
  `from_version_id` binary(16) NOT NULL,
  `to_version_id` binary(16) NOT NULL,
  `diff_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_version_diff_pair` (`from_version_id`,`to_version_id`),
  CONSTRAINT `fk_diff_from` FOREIGN KEY (`from_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_diff_to` FOREIGN KEY (`to_version_id`) REFERENCES `manuscript_versions` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `auto_save_config` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `auto_save_interval_seconds` int NOT NULL DEFAULT 300,
  `max_auto_versions` int NOT NULL DEFAULT 100,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_auto_save_user` (`user_id`),
  CONSTRAINT `fk_auto_save_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `export_templates` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) DEFAULT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `format` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext COLLATE utf8mb4_unicode_ci,
  `is_default` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_export_template_user` (`user_id`),
  CONSTRAINT `fk_export_template_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `export_jobs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) NOT NULL,
  `template_id` binary(16) DEFAULT NULL,
  `format` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext COLLATE utf8mb4_unicode_ci,
  `chapter_range` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `progress` int NOT NULL DEFAULT 0,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size_bytes` bigint DEFAULT NULL,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `expires_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_export_job_user` (`user_id`),
  KEY `idx_export_job_story` (`story_id`),
  KEY `idx_export_job_manuscript` (`manuscript_id`),
  CONSTRAINT `fk_export_job_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_job_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_job_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_export_job_template` FOREIGN KEY (`template_id`) REFERENCES `export_templates` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `model_registry` (
  `id` binary(16) NOT NULL,
  `model_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `display_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `provider` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `capabilities_json` longtext COLLATE utf8mb4_unicode_ci,
  `max_context_tokens` int NOT NULL DEFAULT 0,
  `max_output_tokens` int NOT NULL DEFAULT 0,
  `cost_per_1k_input` decimal(10,6) NOT NULL DEFAULT 0,
  `cost_per_1k_output` decimal(10,6) NOT NULL DEFAULT 0,
  `supports_streaming` bit(1) NOT NULL,
  `is_available` bit(1) NOT NULL,
  `priority` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_model_registry_key` (`model_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `task_model_routing` (
  `id` binary(16) NOT NULL,
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recommended_model_id` binary(16) NOT NULL,
  `fallback_model_id` binary(16) DEFAULT NULL,
  `routing_strategy` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `config_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_model_routing_task` (`task_type`),
  CONSTRAINT `fk_task_routing_recommended` FOREIGN KEY (`recommended_model_id`) REFERENCES `model_registry` (`id`),
  CONSTRAINT `fk_task_routing_fallback` FOREIGN KEY (`fallback_model_id`) REFERENCES `model_registry` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `user_model_preferences` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `preferred_model_id` binary(16) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_model_pref` (`user_id`,`task_type`),
  CONSTRAINT `fk_user_pref_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_user_pref_model` FOREIGN KEY (`preferred_model_id`) REFERENCES `model_registry` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `model_usage_logs` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `model_id` binary(16) NOT NULL,
  `task_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `input_tokens` int NOT NULL DEFAULT 0,
  `output_tokens` int NOT NULL DEFAULT 0,
  `latency_ms` int NOT NULL DEFAULT 0,
  `cost_estimate` decimal(10,6) NOT NULL DEFAULT 0,
  `success` bit(1) NOT NULL,
  `error_message` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_usage_user_created` (`user_id`,`created_at`),
  KEY `idx_usage_story` (`story_id`),
  CONSTRAINT `fk_usage_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_usage_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE SET NULL,
  CONSTRAINT `fk_usage_model` FOREIGN KEY (`model_id`) REFERENCES `model_registry` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `workspace_layouts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `layout_json` longtext COLLATE utf8mb4_unicode_ci,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_layout_user` (`user_id`),
  CONSTRAINT `fk_layout_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `writing_sessions` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `ended_at` datetime(6) DEFAULT NULL,
  `words_written` int NOT NULL DEFAULT 0,
  `words_deleted` int NOT NULL DEFAULT 0,
  `net_words` int NOT NULL DEFAULT 0,
  `duration_seconds` int NOT NULL DEFAULT 0,
  `chapters_edited_json` longtext COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_session_user` (`user_id`),
  KEY `idx_session_story` (`story_id`),
  CONSTRAINT `fk_session_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_session_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `writing_goals` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `story_id` binary(16) DEFAULT NULL,
  `goal_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_value` int NOT NULL,
  `current_value` int NOT NULL DEFAULT 0,
  `deadline` date DEFAULT NULL,
  `status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_goal_user` (`user_id`),
  CONSTRAINT `fk_goal_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_goal_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `keyboard_shortcuts` (
  `id` binary(16) NOT NULL,
  `user_id` binary(16) NOT NULL,
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shortcut` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `is_custom` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shortcut_user_action` (`user_id`,`action`),
  CONSTRAINT `fk_shortcut_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A pre-v2 database can contain a branch id without the corresponding branch table.
-- Those references cannot be recovered after the branch table is created, so clear
-- only dangling values before adding the V1-equivalent foreign key.
UPDATE `manuscripts` AS manuscript
LEFT JOIN `manuscript_branches` AS branch ON branch.id = manuscript.current_branch_id
SET manuscript.current_branch_id = NULL
WHERE manuscript.current_branch_id IS NOT NULL
  AND branch.id IS NULL;

SET @v3_has_current_branch_fk = (
  SELECT COUNT(*)
  FROM information_schema.key_column_usage
  WHERE table_schema = @v3_schema
    AND table_name = 'manuscripts'
    AND column_name = 'current_branch_id'
    AND referenced_table_name = 'manuscript_branches'
    AND referenced_column_name = 'id'
);
SET @v3_sql = IF(
  @v3_has_current_branch_fk = 0,
  'ALTER TABLE `manuscripts` ADD CONSTRAINT `fk_manuscript_current_branch` FOREIGN KEY (`current_branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE SET NULL',
  'SELECT 1'
);
PREPARE v3_statement FROM @v3_sql;
EXECUTE v3_statement;
DEALLOCATE PREPARE v3_statement;
SET sql_notes = @v3_sql_notes;
