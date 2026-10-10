-- New reports never fabricate provenance for the historical slop_quality_runs rows.
CREATE TABLE language_quality_settings (
    story_id BINARY(16) NOT NULL PRIMARY KEY,
    generation_standard BOOLEAN NOT NULL DEFAULT FALSE,
    check_after_generation BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_language_settings_story FOREIGN KEY (story_id) REFERENCES stories(id) ON DELETE CASCADE
);
CREATE TABLE language_quality_reports (
    id BINARY(16) NOT NULL PRIMARY KEY,
    manuscript_id BINARY(16) NOT NULL,
    scene_id BINARY(16) NOT NULL,
    source_key VARCHAR(64) NOT NULL,
    data_json LONGTEXT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_language_report_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE,
    UNIQUE KEY uk_language_report_source (manuscript_id, scene_id, source_key),
    KEY ix_language_report_scene (manuscript_id, scene_id, created_at)
);
CREATE TABLE language_quality_patches (
    id BINARY(16) NOT NULL PRIMARY KEY,
    report_id BINARY(16) NOT NULL,
    issue_id VARCHAR(80) NOT NULL,
    data_json LONGTEXT NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_language_patch_report FOREIGN KEY (report_id) REFERENCES language_quality_reports(id) ON DELETE CASCADE,
    UNIQUE KEY uk_language_patch_issue (report_id, issue_id)
);
CREATE TABLE language_quality_decisions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    patch_id BINARY(16) NOT NULL,
    request_key VARCHAR(80) NOT NULL,
    action VARCHAR(12) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    result_json LONGTEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_language_decision_patch FOREIGN KEY (patch_id) REFERENCES language_quality_patches(id) ON DELETE CASCADE,
    UNIQUE KEY uk_language_decision_key (patch_id, request_key)
);
