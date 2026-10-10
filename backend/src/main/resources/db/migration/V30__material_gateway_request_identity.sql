-- Preserve the original actor and run for result recovery. No historical identity is guessed.
ALTER TABLE material_semantic_jobs
    ADD COLUMN request_owner_id BINARY(16) NULL,
    ADD COLUMN gateway_user_id BIGINT NULL,
    ADD COLUMN evaluation_run_id VARCHAR(80) NULL;
ALTER TABLE material_processing_jobs
    ADD COLUMN gateway_user_id BIGINT NULL,
    ADD COLUMN evaluation_run_id VARCHAR(80) NULL;
