ALTER TABLE worlds ADD COLUMN overview LONGTEXT NULL;
ALTER TABLE world_prompt_templates ADD COLUMN action_templates_json LONGTEXT NULL;

CREATE TABLE world_assistant_sessions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    world_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    title VARCHAR(120) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_world_assistant_world FOREIGN KEY (world_id) REFERENCES worlds(id) ON DELETE CASCADE,
    INDEX idx_world_assistant_recent (world_id,user_id,updated_at,id)
);
CREATE TABLE world_assistant_turns (
    id BINARY(16) NOT NULL PRIMARY KEY,
    session_id BINARY(16) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    operation_id BINARY(16) NULL,
    frozen_json LONGTEXT NOT NULL,
    plan_request_json LONGTEXT NULL,
    plan_result_json LONGTEXT NULL,
    final_request_json LONGTEXT NULL,
    final_result_json LONGTEXT NULL,
    reads_json LONGTEXT NULL,
    result_json LONGTEXT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_world_assistant_turn_request UNIQUE(session_id,request_key),
    CONSTRAINT uk_world_assistant_turn_operation UNIQUE(operation_id),
    CONSTRAINT fk_world_assistant_turn_session FOREIGN KEY(session_id) REFERENCES world_assistant_sessions(id) ON DELETE CASCADE,
    INDEX idx_world_assistant_turn_history(session_id,created_at,id)
);
CREATE TABLE world_assistant_proposals (
    id BINARY(16) NOT NULL PRIMARY KEY,
    turn_id BINARY(16) NOT NULL,
    item_index INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    revision BIGINT NOT NULL DEFAULT 0,
    data_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_world_assistant_item UNIQUE(turn_id,item_index),
    CONSTRAINT fk_world_assistant_proposal_turn FOREIGN KEY(turn_id) REFERENCES world_assistant_turns(id) ON DELETE CASCADE
);
CREATE TABLE world_assistant_receipts (
    world_id BINARY(16) NOT NULL,
    scope_key VARCHAR(100) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_json LONGTEXT NOT NULL,
    PRIMARY KEY(world_id,scope_key,request_key),
    CONSTRAINT fk_world_assistant_receipt_world FOREIGN KEY(world_id) REFERENCES worlds(id) ON DELETE CASCADE
);
