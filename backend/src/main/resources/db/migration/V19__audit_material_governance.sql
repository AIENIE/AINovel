ALTER TABLE materials ADD COLUMN source VARCHAR(16) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN content_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE material_upload_jobs ADD COLUMN owner_user_id BINARY(16) NULL;
UPDATE material_upload_jobs j JOIN materials m ON j.result_material_id = m.id SET j.owner_user_id = m.user_id;
UPDATE materials m SET source = 'UPLOAD' WHERE EXISTS (SELECT 1 FROM material_upload_jobs j WHERE j.result_material_id = m.id);
CREATE INDEX idx_upload_owner_id ON material_upload_jobs(owner_user_id, id);
CREATE INDEX idx_material_status_id ON materials(status, created_at, id);
CREATE TABLE material_duplicate_fingerprints (
    material_id BINARY(16) NOT NULL PRIMARY KEY,
    content_version BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    terms_json TEXT NOT NULL,
    CONSTRAINT fk_duplicate_fingerprint_material FOREIGN KEY(material_id) REFERENCES materials(id) ON DELETE CASCADE
);
CREATE TABLE material_duplicate_terms (
    term VARCHAR(16) NOT NULL,
    material_id BINARY(16) NOT NULL,
    content_version BIGINT NOT NULL,
    PRIMARY KEY(term, material_id),
    CONSTRAINT fk_duplicate_term_material FOREIGN KEY(material_id) REFERENCES materials(id) ON DELETE CASCADE
);
CREATE TABLE material_duplicate_jobs (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    status VARCHAR(24) NOT NULL,
    execution_token VARCHAR(36),
    comparisons INT NOT NULL DEFAULT 0,
    incomplete BIT NOT NULL DEFAULT 0,
    reason VARCHAR(128),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
CREATE TABLE material_duplicate_results (
    job_id VARCHAR(36) NOT NULL,
    source_id BINARY(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    source_version BIGINT NOT NULL,
    target_version BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    PRIMARY KEY(job_id, source_id, target_id),
    CONSTRAINT fk_duplicate_result_job FOREIGN KEY(job_id) REFERENCES material_duplicate_jobs(id) ON DELETE CASCADE
);
