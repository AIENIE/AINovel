ALTER TABLE worlds ADD COLUMN timeline_json LONGTEXT NULL;

CREATE TABLE story_world_eras (
    id BINARY(16) NOT NULL PRIMARY KEY,
    story_id BINARY(16) NOT NULL,
    world_id BINARY(16) NOT NULL,
    reviewed_version_id BINARY(16) NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    era_ids_json LONGTEXT NOT NULL,
    CONSTRAINT uk_story_world_eras UNIQUE (story_id, world_id),
    CONSTRAINT fk_story_world_eras_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_world_eras_world FOREIGN KEY (world_id) REFERENCES worlds(id) ON DELETE CASCADE,
    CONSTRAINT fk_story_world_eras_version FOREIGN KEY (reviewed_version_id) REFERENCES world_versions(id)
);

-- Keep the receipt when a copied draft is deleted, so a retry never recreates it.
CREATE TABLE world_copy_receipts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    request_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    source_world_id BINARY(16) NOT NULL,
    copied_world_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_world_copy_request UNIQUE (user_id, request_key),
    CONSTRAINT fk_world_copy_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
