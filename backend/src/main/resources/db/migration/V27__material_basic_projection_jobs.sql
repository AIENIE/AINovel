-- Raw revisions commit before any retrieval projection is built.
CREATE TABLE material_basic_jobs (
    revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
    error_code VARCHAR(80) NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT fk_material_basic_revision FOREIGN KEY (revision_id) REFERENCES material_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO material_basic_jobs(revision_id,status,completed_at)
SELECT r.id, CASE WHEN EXISTS(SELECT 1 FROM material_evidence_chunks c WHERE c.revision_id=r.id)
THEN 'COMPLETED' ELSE 'QUEUED' END,
CASE WHEN EXISTS(SELECT 1 FROM material_evidence_chunks c WHERE c.revision_id=r.id)
THEN CURRENT_TIMESTAMP(6) ELSE NULL END
FROM material_revisions r;
