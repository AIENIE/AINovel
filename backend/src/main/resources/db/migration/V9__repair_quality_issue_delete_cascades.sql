-- Older quality schemas created issue-to-run foreign keys without ON DELETE
-- CASCADE. A story deletion now cascades through the run tables, so those
-- legacy constraints must also remove their owned issue rows.

SET @v9_schema = DATABASE();

-- Remove only broken issue rows before reinstating their parent constraints.
SET @v9_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'slop_quality_issues') = 1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'slop_quality_runs') = 1,
  'DELETE issue_row FROM slop_quality_issues issue_row LEFT JOIN slop_quality_runs run_row ON run_row.id = issue_row.run_id WHERE run_row.id IS NULL',
  'SELECT 1');
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;

SET @v9_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'plot_quality_issues') = 1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'plot_quality_runs') = 1,
  'DELETE issue_row FROM plot_quality_issues issue_row LEFT JOIN plot_quality_runs run_row ON run_row.id = issue_row.run_id WHERE run_row.id IS NULL',
  'SELECT 1');
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;

-- Constraint names differ across historical Hibernate schemas. Remove every
-- existing run_id foreign key and recreate the canonical cascade constraint.
SET @v9_drop_slop_issue_fks = (
  SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', kcu.constraint_name, '`') SEPARATOR ', ')
  FROM information_schema.key_column_usage kcu
  WHERE kcu.table_schema = @v9_schema
    AND kcu.table_name = 'slop_quality_issues'
    AND kcu.column_name = 'run_id'
    AND kcu.referenced_table_name = 'slop_quality_runs');
SET @v9_sql = IF(@v9_drop_slop_issue_fks IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE slop_quality_issues ', @v9_drop_slop_issue_fks));
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;

SET @v9_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'slop_quality_issues') = 1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'slop_quality_runs') = 1,
  'ALTER TABLE slop_quality_issues ADD CONSTRAINT fk_slop_issue_run FOREIGN KEY (run_id) REFERENCES slop_quality_runs(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;

SET @v9_drop_plot_issue_fks = (
  SELECT GROUP_CONCAT(CONCAT('DROP FOREIGN KEY `', kcu.constraint_name, '`') SEPARATOR ', ')
  FROM information_schema.key_column_usage kcu
  WHERE kcu.table_schema = @v9_schema
    AND kcu.table_name = 'plot_quality_issues'
    AND kcu.column_name = 'run_id'
    AND kcu.referenced_table_name = 'plot_quality_runs');
SET @v9_sql = IF(@v9_drop_plot_issue_fks IS NULL, 'SELECT 1',
  CONCAT('ALTER TABLE plot_quality_issues ', @v9_drop_plot_issue_fks));
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;

SET @v9_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'plot_quality_issues') = 1
  AND (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = @v9_schema AND table_name = 'plot_quality_runs') = 1,
  'ALTER TABLE plot_quality_issues ADD CONSTRAINT fk_plot_issue_run FOREIGN KEY (run_id) REFERENCES plot_quality_runs(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE v9_statement FROM @v9_sql; EXECUTE v9_statement; DEALLOCATE PREPARE v9_statement;
