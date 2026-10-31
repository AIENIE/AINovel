-- Read-only verification after V36-V50. Compare the existing prefix with the pre-execution receipt.

SELECT version, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank;

SHOW CREATE TABLE `ai_credit_reservations`;

SHOW CREATE TABLE `ai_model_catalog_state`;

SHOW CREATE TABLE `ai_model_options`;

SHOW CREATE TABLE `ai_model_quotes`;

SHOW CREATE TABLE `ai_operation_runs`;

SHOW CREATE TABLE `ai_validation_budgets`;

SHOW CREATE TABLE `ai_validation_calls`;

SHOW CREATE TABLE `async_jobs`;

SHOW CREATE TABLE `character_cards`;

SHOW CREATE TABLE `creation_workflow_runs`;

SHOW CREATE TABLE `manuscripts`;

SHOW CREATE TABLE `model_registry`;

SHOW CREATE TABLE `narrative_generation_candidates`;

SHOW CREATE TABLE `outline_ai_results`;

SHOW CREATE TABLE `outlines`;

SHOW CREATE TABLE `scene_generation_runs`;

SHOW CREATE TABLE `slop_quality_runs`;

SHOW CREATE TABLE `stories`;

SHOW CREATE TABLE `story_assistant_proposals`;

SHOW CREATE TABLE `story_assistant_receipts`;

SHOW CREATE TABLE `story_assistant_sessions`;

SHOW CREATE TABLE `story_assistant_turns`;

SHOW CREATE TABLE `story_world_eras`;

SHOW CREATE TABLE `task_model_routing`;

SHOW CREATE TABLE `user_model_preferences`;

SHOW CREATE TABLE `world_assistant_proposals`;

SHOW CREATE TABLE `world_assistant_receipts`;

SHOW CREATE TABLE `world_assistant_sessions`;

SHOW CREATE TABLE `world_assistant_turns`;

SHOW CREATE TABLE `world_context_preferences`;

SHOW CREATE TABLE `world_copy_receipts`;

SHOW CREATE TABLE `world_entries`;

SHOW CREATE TABLE `world_prompt_templates`;

SHOW CREATE TABLE `world_publish_receipts`;

SHOW CREATE TABLE `world_relations`;

SHOW CREATE TABLE `world_versions`;

SHOW CREATE TABLE `worlds`;

SHOW CREATE TABLE `writing_agent_runs`;

SHOW CREATE TABLE `writing_agent_steps`;

SHOW CREATE TABLE `writing_assistant_proposals`;

SHOW CREATE TABLE `writing_assistant_sessions`;

SHOW CREATE TABLE `writing_assistant_turns`;

SELECT id, revision, last_success_at FROM ai_model_catalog_state;

SELECT COUNT(*) AS invalid_baseline_hashes FROM world_versions WHERE origin='MIGRATION_BASELINE' AND content_hash<>SHA2(snapshot_json,256);

SELECT COUNT(*) AS invalid_baseline_times FROM world_versions v JOIN flyway_schema_history m ON m.version='43' AND m.success=1 WHERE v.origin='MIGRATION_BASELINE' AND TIMESTAMPDIFF(SECOND,'1970-01-01 00:00:00',v.created_at)<>UNIX_TIMESTAMP(m.installed_on);

SELECT COUNT(*) AS published_versions FROM world_versions WHERE origin='PUBLISHED';

SELECT COUNT(*) AS original_validation_calls FROM ai_validation_calls;
