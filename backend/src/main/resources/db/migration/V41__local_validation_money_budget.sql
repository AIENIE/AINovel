ALTER TABLE ai_validation_budgets
    ADD COLUMN currency VARCHAR(3) NULL,
    ADD COLUMN cost_limit DECIMAL(19,6) NULL,
    ADD COLUMN settled_cost DECIMAL(19,6) NOT NULL DEFAULT 0,
    ADD COLUMN reserved_cost DECIMAL(19,6) NOT NULL DEFAULT 0;
ALTER TABLE ai_validation_calls
    ADD COLUMN budget_id VARCHAR(160) NULL,
    ADD COLUMN request_hash VARCHAR(64) NULL,
    ADD COLUMN currency VARCHAR(3) NULL,
    ADD COLUMN price_version_id BIGINT NULL,
    ADD COLUMN price_sha256 VARCHAR(64) NULL,
    ADD COLUMN max_cost DECIMAL(19,6) NULL,
    ADD COLUMN settled_cost DECIMAL(19,6) NULL,
    ADD COLUMN settlement_state VARCHAR(32) NULL;
CREATE INDEX idx_validation_call_request ON ai_validation_calls(run_id,request_id);
