-- Keep local administrator identities out of the ordinary users domain while
-- preserving legacy experiments that were created by an SSO user row.
ALTER TABLE `g2_evaluation_experiments`
  MODIFY COLUMN `created_by` binary(16) NULL,
  ADD COLUMN `created_by_admin_subject` varchar(255) COLLATE utf8mb4_unicode_ci NULL AFTER `created_by`,
  ADD CONSTRAINT `chk_g2_experiment_creator_exactly_one` CHECK (
    (`created_by` IS NOT NULL AND `created_by_admin_subject` IS NULL)
    OR (`created_by` IS NULL AND `created_by_admin_subject` IS NOT NULL)
  );
