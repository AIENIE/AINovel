-- Opt-in only. Append-only branch documents preserve author decisions and invalidations.
ALTER TABLE plot_quality_runs ADD COLUMN isolation_stamp_json LONGTEXT NULL, ADD COLUMN isolation_context LONGTEXT NULL;
CREATE TABLE narrative_context_settings (
    story_id BINARY(16) NOT NULL PRIMARY KEY,
    enabled BIT NOT NULL DEFAULT 0,
    revision BIGINT NOT NULL DEFAULT 0,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_context_settings_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE TABLE narrative_context_revisions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    branch_id BINARY(16) NOT NULL,
    revision BIGINT NOT NULL,
    idempotency_key VARCHAR(128),
    request_hash VARCHAR(64),
    document_json LONGTEXT NOT NULL,
    receipt_json LONGTEXT,
    created_at DATETIME(6) NOT NULL,
    author_id BINARY(16),
    CONSTRAINT uq_context_revision UNIQUE (branch_id, revision),
    CONSTRAINT uq_context_idempotency UNIQUE (branch_id, idempotency_key),
    CONSTRAINT fk_context_revision_branch FOREIGN KEY (branch_id) REFERENCES manuscript_branches(id) ON DELETE CASCADE
);
CREATE TABLE narrative_generation_candidates (
    id BINARY(16) NOT NULL PRIMARY KEY,
    manuscript_id BINARY(16) NOT NULL,
    branch_id BINARY(16) NOT NULL,
    scene_id BINARY(16) NOT NULL,
    content LONGTEXT NOT NULL,
    stamp_json LONGTEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_context_candidate_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE,
    CONSTRAINT fk_context_candidate_branch FOREIGN KEY (branch_id) REFERENCES manuscript_branches(id) ON DELETE CASCADE
);
CREATE TABLE ai_validation_budgets (
    id VARCHAR(80) NOT NULL PRIMARY KEY,
    used INT NOT NULL DEFAULT 0,
    call_limit INT NOT NULL
);
CREATE TABLE ai_validation_calls (
    id BINARY(16) NOT NULL PRIMARY KEY,
    run_id VARCHAR(80) NOT NULL,
    attempt INT NOT NULL,
    request_id VARCHAR(160),
    model VARCHAR(128),
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uq_validation_attempt UNIQUE (run_id, attempt),
    CONSTRAINT fk_validation_run FOREIGN KEY (run_id) REFERENCES ai_validation_budgets(id)
);
