-- Nullable additions preserve the original H2 candidate contract and all historical rows.
ALTER TABLE narrative_generation_candidates
    ADD COLUMN operation_id BINARY(16) NULL,
    ADD COLUMN generation_sequence INT NULL,
    ADD COLUMN manuscript_version BIGINT NULL,
    ADD COLUMN min_han INT NULL,
    ADD COLUMN max_han INT NULL,
    ADD COLUMN actual_han INT NULL,
    ADD COLUMN quality_status VARCHAR(32) NULL,
    ADD COLUMN source_kind VARCHAR(32) NULL,
    ADD COLUMN metadata_json LONGTEXT NULL,
    ADD COLUMN request_json LONGTEXT NULL,
    ADD COLUMN adopted_version BIGINT NULL,
    ADD CONSTRAINT uk_scene_candidate_operation_sequence UNIQUE (operation_id, generation_sequence),
    ADD INDEX idx_scene_candidates (manuscript_id, branch_id, scene_id, created_at);
