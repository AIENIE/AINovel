ALTER TABLE worlds
    ADD COLUMN legacy_modules_json LONGTEXT NULL,
    ADD COLUMN draft_content_hash VARCHAR(64) NULL;

CREATE TABLE world_entries (
    id BINARY(16) NOT NULL PRIMARY KEY,
    world_id BINARY(16) NOT NULL,
    type VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    summary LONGTEXT NULL,
    content LONGTEXT NULL,
    aliases_json LONGTEXT NOT NULL,
    tags_json LONGTEXT NOT NULL,
    legacy_module_key VARCHAR(255) NULL,
    legacy_field_key VARCHAR(255) NULL,
    CONSTRAINT fk_world_entry_world FOREIGN KEY(world_id) REFERENCES worlds(id),
    CONSTRAINT uq_world_entry_origin UNIQUE(world_id,legacy_module_key,legacy_field_key),
    INDEX idx_world_entry_world(world_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE world_relations (
    id BINARY(16) NOT NULL PRIMARY KEY,
    world_id BINARY(16) NOT NULL,
    source_id BINARY(16) NOT NULL,
    target_id BINARY(16) NOT NULL,
    type VARCHAR(255) NOT NULL,
    description LONGTEXT NULL,
    CONSTRAINT fk_world_relation_world FOREIGN KEY(world_id) REFERENCES worlds(id),
    CONSTRAINT fk_world_relation_source FOREIGN KEY(source_id) REFERENCES world_entries(id),
    CONSTRAINT fk_world_relation_target FOREIGN KEY(target_id) REFERENCES world_entries(id),
    INDEX idx_world_relation_world(world_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE world_versions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    world_id BINARY(16) NOT NULL,
    sequence_number INT NOT NULL,
    origin VARCHAR(255) NOT NULL,
    legacy_version VARCHAR(255) NULL,
    draft_revision BIGINT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    snapshot_json LONGTEXT NOT NULL,
    release_note LONGTEXT NULL,
    request_key VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_world_version_world FOREIGN KEY(world_id) REFERENCES worlds(id),
    CONSTRAINT uq_world_version_sequence UNIQUE(world_id,sequence_number),
    CONSTRAINT uq_world_version_request UNIQUE(world_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE stories
    ADD COLUMN world_version_id BINARY(16) NULL,
    ADD COLUMN world_binding_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_story_world_version FOREIGN KEY(world_version_id) REFERENCES world_versions(id);
ALTER TABLE outlines
    ADD COLUMN world_version_id BINARY(16) NULL,
    ADD COLUMN world_binding_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_outline_world_version FOREIGN KEY(world_version_id) REFERENCES world_versions(id);
ALTER TABLE manuscripts
    ADD COLUMN world_version_id BINARY(16) NULL,
    ADD COLUMN world_binding_revision BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_manuscript_world_version FOREIGN KEY(world_version_id) REFERENCES world_versions(id);

CREATE TABLE world_publish_receipts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    world_id BINARY(16) NOT NULL,
    version_id BINARY(16) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    CONSTRAINT fk_world_publish_world FOREIGN KEY(world_id) REFERENCES worlds(id),
    CONSTRAINT fk_world_publish_version FOREIGN KEY(version_id) REFERENCES world_versions(id),
    CONSTRAINT uq_world_publish_request UNIQUE(world_id,request_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
