-- Fast H2 fixtures only. MySQL migrations, ngram and locking are verified by the separate MySQL suite.
CREATE TABLE IF NOT EXISTS material_source_links (
 id CHAR(36) PRIMARY KEY, owner_id BINARY(16) NOT NULL, manuscript_id BINARY(16) NOT NULL,
 scene_id VARCHAR(100) NOT NULL, branch_id CHAR(36) NOT NULL, body_version CHAR(36) NOT NULL,
 revision_id CHAR(36) NOT NULL, relation_type VARCHAR(24) NOT NULL, start_cp INT NOT NULL,
 end_cp INT NOT NULL, quote CLOB NOT NULL, body_quote CLOB NOT NULL,
 state VARCHAR(24) NOT NULL DEFAULT 'CURRENT', request_key VARCHAR(80), request_hash CHAR(64),
 body_block_id VARCHAR(80), body_start_cp INT, body_end_cp INT, body_conversion_version VARCHAR(80),
 report_task_id CHAR(36), report_finding_index INT,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS material_processing_jobs (
 id CHAR(36) PRIMARY KEY, owner_id BINARY(16) NOT NULL, kind VARCHAR(24) NOT NULL,
 request_key VARCHAR(80), request_hash CHAR(64), source_revision CHAR(36), manuscript_id BINARY(16),
 branch_id CHAR(36), body_version CHAR(36), input_json CLOB, result_json CLOB, provider_response_json CLOB,
 status VARCHAR(24) NOT NULL DEFAULT 'QUEUED', error_code VARCHAR(80),
 lease_token CHAR(36), lease_until TIMESTAMP, call_limit INT, calls_reserved INT DEFAULT 0,
 gateway_user_id BIGINT, evaluation_run_id VARCHAR(80),
 budget_waits INT NOT NULL DEFAULT 0, not_before TIMESTAMP,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
