-- Optional manual cleanup for legacy AINovel admin overlap fields.
-- Review and back up production data before running.

ALTER TABLE global_settings
  DROP COLUMN registration_enabled,
  DROP COLUMN check_in_min_points,
  DROP COLUMN check_in_max_points,
  DROP COLUMN smtp_host,
  DROP COLUMN smtp_port,
  DROP COLUMN smtp_username,
  DROP COLUMN smtp_password,
  DROP COLUMN llm_base_url,
  DROP COLUMN llm_model_name,
  DROP COLUMN llm_api_key_encrypted;

-- Check-in is no longer exposed by AINovel. Drop only after confirming no
-- operational reports still need historical check-in records.
DROP TABLE IF EXISTS checkin_records;

ALTER TABLE users
  DROP COLUMN last_check_in_at;
