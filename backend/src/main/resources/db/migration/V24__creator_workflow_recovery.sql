ALTER TABLE ai_operation_runs MODIFY request_id varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
ALTER TABLE ai_operation_steps MODIFY request_id varchar(160) COLLATE utf8mb4_unicode_ci DEFAULT NULL;

CREATE TABLE manuscript_creation_receipts (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    outline_id BINARY(16) NOT NULL,
    request_key VARCHAR(36) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    manuscript_id BINARY(16),
    response_json LONGTEXT NOT NULL,
    CONSTRAINT uq_manuscript_creation_request UNIQUE (user_id, outline_id, request_key),
    CONSTRAINT fk_creation_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_creation_outline FOREIGN KEY (outline_id) REFERENCES outlines(id) ON DELETE CASCADE,
    CONSTRAINT fk_creation_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE SET NULL
);

ALTER TABLE ai_validation_budgets
    ADD COLUMN provider_attempt_limit INT DEFAULT NULL,
    ADD COLUMN reserved_provider_attempts INT NOT NULL DEFAULT 0;
ALTER TABLE ai_validation_calls
    ADD COLUMN operation_kind VARCHAR(32) DEFAULT NULL,
    ADD COLUMN reserved_provider_attempts INT NOT NULL DEFAULT 0;
