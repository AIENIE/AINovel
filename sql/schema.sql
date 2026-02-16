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
