ALTER TABLE manuscripts ADD COLUMN content_storage_version INT NOT NULL DEFAULT 1;
CREATE TABLE manuscript_scene_contents (
    manuscript_id BINARY(16) NOT NULL,
    scene_id BINARY(16) NOT NULL,
    content LONGTEXT NOT NULL,
    word_count BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY(manuscript_id, scene_id),
    CONSTRAINT fk_scene_content_manuscript FOREIGN KEY(manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE
);
CREATE TABLE manuscript_content_migration (
    manuscript_id BINARY(16) NOT NULL PRIMARY KEY,
    scene_count INT NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    verified_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_content_migration_manuscript FOREIGN KEY(manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE
);
