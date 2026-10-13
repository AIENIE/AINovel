CREATE TABLE writing_assistant_sessions (
    id BINARY(16) NOT NULL PRIMARY KEY,
    user_id BINARY(16) NOT NULL,
    manuscript_id BINARY(16) NOT NULL,
    branch_id BINARY(16) NOT NULL,
    scene_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_assistant_session UNIQUE (user_id,manuscript_id,branch_id,scene_id),
    CONSTRAINT fk_assistant_session_manuscript FOREIGN KEY (manuscript_id) REFERENCES manuscripts(id) ON DELETE CASCADE
);
CREATE TABLE writing_assistant_turns (
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
    CONSTRAINT uk_assistant_turn_request UNIQUE (session_id,request_key),
    CONSTRAINT uk_assistant_turn_operation UNIQUE (operation_id),
    CONSTRAINT fk_assistant_turn_session FOREIGN KEY (session_id) REFERENCES writing_assistant_sessions(id) ON DELETE CASCADE,
    INDEX idx_assistant_turn_history (session_id,created_at,id)
);
CREATE TABLE writing_assistant_proposals (
    id BINARY(16) NOT NULL PRIMARY KEY,
    turn_id BINARY(16) NOT NULL,
    status VARCHAR(16) NOT NULL,
    data_json LONGTEXT NOT NULL,
    receipts_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_assistant_proposal_turn UNIQUE (turn_id),
    CONSTRAINT fk_assistant_proposal_turn FOREIGN KEY (turn_id) REFERENCES writing_assistant_turns(id) ON DELETE CASCADE
);
