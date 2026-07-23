-- Backfill columns absent from databases that were baselined before the complete V1 quality schema.
-- Every alteration is conditional so databases created from the current V1 baseline remain unchanged.

SET @v6_sql_notes = @@SESSION.sql_notes;
SET sql_notes = 0;
SET @v6_schema = DATABASE();
SET @v6_has_issue_table = (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=@v6_schema AND table_name='slop_quality_issues');

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='char_start')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `char_start` int DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='char_end')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `char_end` int DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='quote')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `quote` varchar(800) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='module')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `module` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='pattern_id')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `pattern_id` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='issue_type')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `issue_type` varchar(80) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='evidence_level')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `evidence_level` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='alternative_explanations_json')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `alternative_explanations_json` longtext COLLATE utf8mb4_unicode_ci', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET @v6_sql = IF(@v6_has_issue_table=1 AND (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=@v6_schema AND table_name='slop_quality_issues' AND column_name='repair_hint')=0,
  'ALTER TABLE `slop_quality_issues` ADD COLUMN `repair_hint` varchar(800) COLLATE utf8mb4_unicode_ci DEFAULT NULL', 'SELECT 1');
PREPARE v6_statement FROM @v6_sql; EXECUTE v6_statement; DEALLOCATE PREPARE v6_statement;

SET sql_notes = @v6_sql_notes;
