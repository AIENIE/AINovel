-- Make story and outline deletion remove the complete owned content tree.

ALTER TABLE `character_cards`
  DROP FOREIGN KEY `FK8fl6goog1m0be12r3huk0cxbu`,
  ADD CONSTRAINT `fk_character_card_story`
    FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE;

ALTER TABLE `outlines`
  DROP FOREIGN KEY `FKij56qu3qp6w5ykg4cxauyo1h7`,
  ADD CONSTRAINT `fk_outline_story`
    FOREIGN KEY (`story_id`) REFERENCES `stories` (`id`) ON DELETE CASCADE;

ALTER TABLE `manuscripts`
  DROP FOREIGN KEY `FKm1nvu0uvtf1nmbu4pgux6kdro`,
  ADD CONSTRAINT `fk_manuscript_outline`
    FOREIGN KEY (`outline_id`) REFERENCES `outlines` (`id`) ON DELETE CASCADE;

-- Historical quality runs were created without parent constraints. Remove only
-- rows whose required story or manuscript parent is already gone before adding
-- the durable constraints. Issue rows are removed by their existing run FKs.
-- Some very old baselined schemas predate the quality tables. Use guarded DDL
-- so those schemas can still advance; current schemas receive the constraints.
SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'slop_drift_runs') = 1,
  'DELETE r FROM slop_drift_runs r LEFT JOIN stories s ON s.id=r.story_id LEFT JOIN manuscripts m ON m.id=r.manuscript_id WHERE s.id IS NULL OR m.id IS NULL',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;

SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'slop_quality_runs') = 1,
  'DELETE r FROM slop_quality_runs r LEFT JOIN stories s ON s.id=r.story_id LEFT JOIN manuscripts m ON m.id=r.manuscript_id WHERE s.id IS NULL OR m.id IS NULL',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;

SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'plot_quality_runs') = 1,
  'DELETE r FROM plot_quality_runs r LEFT JOIN stories s ON s.id=r.story_id LEFT JOIN manuscripts m ON m.id=r.manuscript_id WHERE s.id IS NULL OR m.id IS NULL',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;

SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'slop_drift_runs') = 1,
  'ALTER TABLE slop_drift_runs ADD CONSTRAINT fk_slop_drift_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE, ADD CONSTRAINT fk_slop_drift_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;

SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'slop_quality_runs') = 1,
  'ALTER TABLE slop_quality_runs ADD CONSTRAINT fk_slop_run_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE, ADD CONSTRAINT fk_slop_run_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;

SET @quality_sql = IF(
  (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = 'plot_quality_runs') = 1,
  'ALTER TABLE plot_quality_runs ADD CONSTRAINT fk_plot_run_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE, ADD CONSTRAINT fk_plot_run_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE',
  'SELECT 1');
PREPARE quality_stmt FROM @quality_sql; EXECUTE quality_stmt; DEALLOCATE PREPARE quality_stmt;
