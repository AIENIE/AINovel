CREATE TABLE world_context_preferences (
    scope_key VARCHAR(160) NOT NULL,
    world_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    reviewed_version_id BINARY(16) NOT NULL,
    choices_json LONGTEXT NOT NULL,
    reviewed_hashes_json LONGTEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (scope_key,world_id),
    CONSTRAINT fk_world_context_world FOREIGN KEY (world_id) REFERENCES worlds(id) ON DELETE CASCADE,
    CONSTRAINT fk_world_context_version FOREIGN KEY (reviewed_version_id) REFERENCES world_versions(id)
);

CREATE TABLE outline_ai_results (
    id BINARY(16) NOT NULL PRIMARY KEY,
    outline_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    operation_id BINARY(16) NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    frozen_json LONGTEXT NOT NULL,
    raw_result_json LONGTEXT NULL,
    candidate_json LONGTEXT NULL,
    receipt_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_outline_ai_outline FOREIGN KEY (outline_id) REFERENCES outlines(id) ON DELETE CASCADE,
    UNIQUE KEY uk_outline_ai_request (outline_id,request_key),
    UNIQUE KEY uk_outline_ai_operation (operation_id),
    INDEX idx_outline_ai_recent (outline_id,created_at,id)
);
