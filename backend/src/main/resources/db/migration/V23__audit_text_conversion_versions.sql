-- Historical rows remain NULL: their existing evidence, hashes and offsets are unchanged.
ALTER TABLE slop_quality_runs ADD COLUMN text_conversion_version VARCHAR(64) NULL;
ALTER TABLE plot_quality_runs ADD COLUMN text_conversion_version VARCHAR(64) NULL;
ALTER TABLE slop_drift_runs ADD COLUMN text_conversion_version VARCHAR(64) NULL;
ALTER TABLE narrative_approvals ADD COLUMN text_conversion_version VARCHAR(64) NULL;
ALTER TABLE narrative_extractions ADD COLUMN text_conversion_version VARCHAR(64) NULL;
