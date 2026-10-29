-- Native protocol receipts remain private and are saved atomically with the original result.
ALTER TABLE ai_credit_reservations ADD COLUMN result_native_response_json LONGTEXT NULL;

CREATE TABLE writing_agent_runs (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    manuscript_id BINARY(16) NOT NULL,
    branch_id BINARY(16) NOT NULL,
    scene_id BINARY(16) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    operation_id BINARY(16) NULL,
    frozen_json LONGTEXT NOT NULL,
    state_json LONGTEXT NOT NULL,
    max_cost_cny DECIMAL(18,6) NOT NULL,
    max_project_credits BIGINT NOT NULL,
    decision_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_writing_agent_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE,
    CONSTRAINT uk_writing_agent_request UNIQUE (user_id,manuscript_id,scene_id,request_key),
    INDEX idx_writing_agent_recent (user_id,manuscript_id,scene_id,created_at,id)
);

CREATE TABLE writing_agent_steps (
    id BINARY(16) NOT NULL PRIMARY KEY,
    run_id BINARY(16) NOT NULL,
    sequence_number INT NOT NULL,
    call_key VARCHAR(180) NOT NULL,
    kind VARCHAR(16) NOT NULL,
    feature_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    request_json LONGTEXT NOT NULL,
    result_json LONGTEXT NULL,
    gateway_request_base64 LONGTEXT NULL,
    budget_json LONGTEXT NULL,
    gateway_result_base64 LONGTEXT NULL,
    settlement_state VARCHAR(32) NULL,
    reserved_cny DECIMAL(18,6) NOT NULL DEFAULT 0,
    settled_cny DECIMAL(18,6) NULL,
    reserved_project_credits BIGINT NOT NULL DEFAULT 0,
    settled_project_credits BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_writing_agent_step FOREIGN KEY (run_id) REFERENCES writing_agent_runs(id) ON DELETE CASCADE,
    CONSTRAINT uk_writing_agent_step UNIQUE (run_id,sequence_number),
    CONSTRAINT uk_writing_agent_call UNIQUE (run_id,call_key)
);
