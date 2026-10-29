ALTER TABLE model_registry
    ADD COLUMN gateway_model_id BIGINT NULL,
    ADD COLUMN thinking_json LONGTEXT NULL,
    ADD COLUMN last_synced_at TIMESTAMP(6) NULL,
    ADD COLUMN supports_image_input BIT(1) NOT NULL DEFAULT b'0';
ALTER TABLE task_model_routing
    ADD COLUMN billing_enabled BIT(1) NOT NULL DEFAULT b'1',
    ADD COLUMN configuration_revision BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN default_option_id VARCHAR(36) NULL;
ALTER TABLE user_model_preferences ADD COLUMN preferred_option_id VARCHAR(36) NULL;
CREATE TABLE ai_model_options (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    task_type VARCHAR(50) NOT NULL,
    model_id BINARY(16) NOT NULL,
    settings_json LONGTEXT NOT NULL,
    active BIT(1) NOT NULL,
    position_index INT NOT NULL,
    CONSTRAINT fk_ai_option_model FOREIGN KEY(model_id) REFERENCES model_registry(id),
    INDEX idx_ai_option_feature(task_type,active,position_index)
);
CREATE TABLE ai_model_catalog_state (
    id INT NOT NULL PRIMARY KEY,
    revision BIGINT NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP(6) NULL,
    last_success_at TIMESTAMP(6) NULL,
    last_error VARCHAR(100) NULL
);
INSERT INTO ai_model_catalog_state(id,revision) VALUES(1,0);
CREATE TABLE ai_model_quotes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    input_hash VARCHAR(64) NOT NULL,
    target_path VARCHAR(2000) NOT NULL,
    request_json LONGTEXT NOT NULL,
    business_hash VARCHAR(64) NOT NULL,
    configuration_revision BIGINT NOT NULL,
    selections_json LONGTEXT NOT NULL,
    accepted_request_key VARCHAR(160) NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_ai_quote_user FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_ai_quote_user_expiry(user_id,expires_at)
);
ALTER TABLE ai_credit_reservations
    ADD COLUMN selection_json LONGTEXT NULL,
    ADD COLUMN actual_model_key VARCHAR(100) NULL,
    ADD COLUMN frozen_request_json LONGTEXT NULL,
    ADD COLUMN request_identity VARCHAR(160) NULL,
    ADD COLUMN operation_id BINARY(16) NULL,
    ADD INDEX idx_ai_reservation_operation(operation_id,status);
ALTER TABLE ai_operation_runs ADD COLUMN model_selections_json LONGTEXT NULL;
ALTER TABLE async_jobs ADD COLUMN model_selections_json LONGTEXT NULL;
ALTER TABLE creation_workflow_runs ADD COLUMN model_selections_json LONGTEXT NULL;
ALTER TABLE scene_generation_runs ADD COLUMN model_selection_json LONGTEXT NULL;
