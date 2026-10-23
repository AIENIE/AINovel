-- V43 used the database session's wall clock for a UTC application timestamp.
-- Recover the actual migration instant from Flyway's TIMESTAMP column. Epoch
-- arithmetic is independent of the current connection's time zone.
UPDATE world_versions v
JOIN flyway_schema_history migration ON migration.version='43' AND migration.success=1
SET v.created_at=TIMESTAMPADD(SECOND,UNIX_TIMESTAMP(migration.installed_on),CAST('1970-01-01 00:00:00' AS DATETIME(6)))
WHERE v.origin='MIGRATION_BASELINE';
