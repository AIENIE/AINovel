-- Confirmed material metadata belongs to a specific immutable source revision.
ALTER TABLE material_entity_sources ADD COLUMN source_version BIGINT NOT NULL DEFAULT 0;
UPDATE material_entity_sources s JOIN materials m ON m.id=s.material_id
SET s.source_version=m.content_version;

CREATE TABLE material_confirmed_annotations (
    id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    owner_id BINARY(16) NOT NULL,
    revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    candidate_index INT NOT NULL,
    annotation_kind VARCHAR(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    candidate_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_material_confirmation (owner_id, task_id, candidate_index),
    KEY ix_material_confirmation_revision (revision_id),
    CONSTRAINT fk_material_confirmation_revision FOREIGN KEY (revision_id) REFERENCES material_revisions(id),
    CONSTRAINT fk_material_confirmation_task FOREIGN KEY (task_id) REFERENCES material_processing_jobs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE material_hint_slots (
    owner_id BINARY(16) NOT NULL,
    manuscript_id BINARY(16) NOT NULL,
    scene_id VARCHAR(100) NOT NULL,
    cache_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_json LONGTEXT NULL,
    observed_at DATETIME(6) NULL,
    PRIMARY KEY (owner_id, manuscript_id, scene_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
