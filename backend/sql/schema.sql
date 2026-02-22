-- AINovel schema reference (MySQL)
-- Generated from the running `ainovel` database on 2026-01-13.
-- UUID primary keys are stored as `binary(16)` (Hibernate/Spring Boot default mapping).

CREATE TABLE `character_cards` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `details` longtext COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `relationships` longtext COLLATE utf8mb4_unicode_ci,
  `synopsis` longtext COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `story_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8fl6goog1m0be12r3huk0cxbu` (`story_id`),
  CONSTRAINT `FK8fl6goog1m0be12r3huk0cxbu` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `email_verification_codes` (
  `id` binary(16) NOT NULL,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `purpose` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `used` bit(1) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `global_settings` (
  `id` binary(16) NOT NULL,
  `check_in_max_points` int NOT NULL,
  `check_in_min_points` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `maintenance_mode` bit(1) NOT NULL,
  `registration_enabled` bit(1) NOT NULL,
  `smtp_host` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `smtp_password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `smtp_port` int DEFAULT NULL,
  `smtp_username` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `llm_api_key_encrypted` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `llm_base_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `llm_model_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `manuscripts` (
  `id` binary(16) NOT NULL,
  `character_logs_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `sections_json` longtext COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `outline_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm1nvu0uvtf1nmbu4pgux6kdro` (`outline_id`),
  CONSTRAINT `FKm1nvu0uvtf1nmbu4pgux6kdro` FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `material_upload_jobs` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `progress` int NOT NULL,
  `result_material_id` binary(16) DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `materials` (
  `id` binary(16) NOT NULL,
  `content` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `entities_json` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags_json` longtext COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK32wqk9p2efffrkb1l6yvkysou` (`user_id`),
  CONSTRAINT `FK32wqk9p2efffrkb1l6yvkysou` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `outlines` (
  `id` binary(16) NOT NULL,
  `content_json` longtext COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `story_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKij56qu3qp6w5ykg4cxauyo1h7` (`story_id`),
  CONSTRAINT `FKij56qu3qp6w5ykg4cxauyo1h7` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `prompt_templates` (
  `id` binary(16) NOT NULL,
  `manuscript_section` longtext COLLATE utf8mb4_unicode_ci,
  `outline_chapter` longtext COLLATE utf8mb4_unicode_ci,
  `refine_with_instruction` longtext COLLATE utf8mb4_unicode_ci,
  `refine_without_instruction` longtext COLLATE utf8mb4_unicode_ci,
  `story_creation` longtext COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK8dm9s6fl88ua6j9y6w90hun67` (`user_id`),
  CONSTRAINT `FK49ya7kke8ffdsxx5djymew73k` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `stories` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `genre` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `synopsis` longtext COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tone` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `world_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKshv2ytgbsn9w9mpu43mc6ln6j` (`user_id`),
  CONSTRAINT `FKshv2ytgbsn9w9mpu43mc6ln6j` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `user_roles` (
  `user_id` binary(16) NOT NULL,
  `role` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKhfh9dx7w3ubf1co1vdev94g3f` (`user_id`),
  CONSTRAINT `FKhfh9dx7w3ubf1co1vdev94g3f` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `users` (
  `id` binary(16) NOT NULL,
  `avatar_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `banned` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `credits` double NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_check_in_at` datetime(6) DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_uid` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`),
  UNIQUE KEY `UKl6igvmj08ovhtjfdxf9cm493c` (`remote_uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `world_prompt_templates` (
  `id` binary(16) NOT NULL,
  `field_refine` longtext COLLATE utf8mb4_unicode_ci,
  `final_templates_json` longtext COLLATE utf8mb4_unicode_ci,
  `modules_json` longtext COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKjyf0gnbhdrdp109akcggleif3` (`user_id`),
  CONSTRAINT `FK2tiidjlqeb20m1ivkw30u8diu` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `worlds` (
  `id` binary(16) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `creative_intent` longtext COLLATE utf8mb4_unicode_ci,
  `module_progress_json` longtext COLLATE utf8mb4_unicode_ci,
  `modules_json` longtext COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` longtext COLLATE utf8mb4_unicode_ci,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tagline` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `themes_json` longtext COLLATE utf8mb4_unicode_ci,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_id` binary(16) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9mp0grigxj1tudlm6p9e6gxw0` (`user_id`),
  CONSTRAINT `FK9mp0grigxj1tudlm6p9e6gxw0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- v2 incremental schema (2026-02-17)
ALTER TABLE `manuscripts`
  ADD COLUMN `current_branch_id` binary(16) DEFAULT NULL;

CREATE TABLE `lorebook_entries` (
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

CREATE TABLE `context_snapshots` (
  `id` binary(16) NOT NULL,
  `story_id` binary(16) NOT NULL,
  `manuscript_id` binary(16) DEFAULT NULL,
  `chapter_index` int NOT NULL,
  `scene_index` int NOT NULL,
  `active_characters_json` longtext COLLATE utf8mb4_unicode_ci,
  `active_locations_json` longtext COLLATE utf8mb4_unicode_ci,
  `timeline_position` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `summary` longtext COLLATE utf8mb4_unicode_ci,
  `emotional_tone` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `word_count` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_context_story` (`story_id`),
  KEY `idx_context_story_chapter_scene` (`story_id`,`chapter_index`,`scene_index`),
  CONSTRAINT `fk_context_story` FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_context_manuscript` FOREIGN KEY (`manuscript_id`) REFERENCES `manuscripts` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `entity_extractions` (
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

CREATE TABLE `style_profiles` (
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

CREATE TABLE `style_profile_scene_overrides` (
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

CREATE TABLE `character_voices` (
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

CREATE TABLE `style_analysis_jobs` (
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

CREATE TABLE `beta_reader_reports` (
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

CREATE TABLE `continuity_issues` (
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

CREATE TABLE `analysis_jobs` (
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

CREATE TABLE `manuscript_branches` (
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

CREATE TABLE `manuscript_versions` (
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

CREATE TABLE `version_diffs` (
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

CREATE TABLE `auto_save_config` (
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

CREATE TABLE `export_templates` (
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

CREATE TABLE `export_jobs` (
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

CREATE TABLE `model_registry` (
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

CREATE TABLE `task_model_routing` (
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

CREATE TABLE `user_model_preferences` (
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

CREATE TABLE `model_usage_logs` (
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

CREATE TABLE `workspace_layouts` (
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

CREATE TABLE `writing_sessions` (
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

CREATE TABLE `writing_goals` (
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

CREATE TABLE `keyboard_shortcuts` (
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

ALTER TABLE `manuscripts`
  ADD CONSTRAINT `fk_manuscript_current_branch`
  FOREIGN KEY (`current_branch_id`) REFERENCES `manuscript_branches` (`id`) ON DELETE SET NULL;

-- economy local billing incremental schema (2026-02-20)
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

CREATE TABLE `redeem_codes` (
  `id` binary(16) NOT NULL,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `grant_amount` bigint NOT NULL,
  `max_uses` int DEFAULT NULL,
  `used_count` int NOT NULL,
  `starts_at` datetime(6) DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `stackable` bit(1) NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_redeem_code_code` (`code`)
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

CREATE TABLE `credit_conversion_orders` (
  `id` binary(16) NOT NULL,
  `order_no` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` binary(16) NOT NULL,
  `idempotency_key` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `requested_amount` bigint NOT NULL,
  `converted_amount` bigint NOT NULL,
  `project_before` bigint NOT NULL,
  `project_after` bigint NOT NULL,
  `public_before` bigint NOT NULL,
  `public_after` bigint NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remote_request_id` varchar(128) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remote_message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conversion_order_no` (`order_no`),
  UNIQUE KEY `uk_conversion_user_idempotency` (`user_id`,`idempotency_key`),
  CONSTRAINT `fk_conversion_order_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
