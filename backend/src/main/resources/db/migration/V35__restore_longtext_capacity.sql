-- Restore historical LONGTEXT capacities without changing data, collation or nullability.
-- Column metadata comes from this database; identifiers below are a fixed allowlist.
SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `ai_operation_runs` MODIFY COLUMN `payload_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_operation_runs' AND COLUMN_NAME = 'payload_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `ai_operation_runs` MODIFY COLUMN `result_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_operation_runs' AND COLUMN_NAME = 'result_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `ai_validation_calls` MODIFY COLUMN `request_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'ai_validation_calls' AND COLUMN_NAME = 'request_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `creation_workflow_runs` MODIFY COLUMN `steps_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'creation_workflow_runs' AND COLUMN_NAME = 'steps_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_approvals` MODIFY COLUMN `blocks_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_approvals' AND COLUMN_NAME = 'blocks_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_approvals` MODIFY COLUMN `position_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_approvals' AND COLUMN_NAME = 'position_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_commits` MODIFY COLUMN `detail_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_commits' AND COLUMN_NAME = 'detail_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_context_revisions` MODIFY COLUMN `document_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_context_revisions' AND COLUMN_NAME = 'document_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_extractions` MODIFY COLUMN `input_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_extractions' AND COLUMN_NAME = 'input_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_generation_candidates` MODIFY COLUMN `content` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_generation_candidates' AND COLUMN_NAME = 'content');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_generation_candidates` MODIFY COLUMN `stamp_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_generation_candidates' AND COLUMN_NAME = 'stamp_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_records` MODIFY COLUMN `assertion_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_records' AND COLUMN_NAME = 'assertion_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `narrative_records` MODIFY COLUMN `dependency_ids_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'narrative_records' AND COLUMN_NAME = 'dependency_ids_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `plot_quality_runs` MODIFY COLUMN `revision_candidate_text` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plot_quality_runs' AND COLUMN_NAME = 'revision_candidate_text');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `plot_quality_runs` MODIFY COLUMN `rewrite_plan_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plot_quality_runs' AND COLUMN_NAME = 'rewrite_plan_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `plot_quality_runs` MODIFY COLUMN `surgical_fixes_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plot_quality_runs' AND COLUMN_NAME = 'surgical_fixes_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `scene_generation_runs` MODIFY COLUMN `context_manifest_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scene_generation_runs' AND COLUMN_NAME = 'context_manifest_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `scene_generation_runs` MODIFY COLUMN `diff_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scene_generation_runs' AND COLUMN_NAME = 'diff_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;

SET @ainovel_longtext_ddl = (SELECT CONCAT('ALTER TABLE `scene_generation_runs` MODIFY COLUMN `tags_json` LONGTEXT CHARACTER SET ', CHARACTER_SET_NAME, ' COLLATE ', COLLATION_NAME, IF(IS_NULLABLE = 'YES', ' NULL', ' NOT NULL')) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'scene_generation_runs' AND COLUMN_NAME = 'tags_json');
PREPARE ainovel_longtext_stmt FROM @ainovel_longtext_ddl;
EXECUTE ainovel_longtext_stmt;
DEALLOCATE PREPARE ainovel_longtext_stmt;
