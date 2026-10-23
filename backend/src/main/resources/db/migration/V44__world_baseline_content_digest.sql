-- V42/V43 are frozen. Complete the migration baseline digest without rewriting its content.
UPDATE world_versions
SET content_hash = SHA2(snapshot_json, 256)
WHERE origin = 'MIGRATION_BASELINE';
