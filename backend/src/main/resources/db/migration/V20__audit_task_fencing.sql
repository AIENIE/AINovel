-- Leases use a fresh UUID per execution; claims/terminal transitions use row locks.
-- Existing task lease_owner columns already fit the execution token.
ALTER TABLE worlds ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE outlines ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;
ALTER TABLE ai_credit_reservations ADD COLUMN lease_expires_at DATETIME(6) NULL;
-- Legacy interrupted reservations require reconciliation; expiry must never cause re-inference.
UPDATE ai_credit_reservations SET lease_expires_at=CURRENT_TIMESTAMP(6) WHERE status='RESERVED';
ALTER TABLE material_chunks ADD COLUMN content_version BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_material_chunk_version ON material_chunks(material_id,content_version);
-- Old projections are intentionally unreadable until rebuilt with a version and execution token.
-- Requeue every material, including rejected ones whose stale vectors need removal.
UPDATE material_index_jobs SET status='queued', attempt_count=0, next_attempt_at=CURRENT_TIMESTAMP(6),
    lease_owner=NULL, lease_expires_at=NULL, error_code=NULL;
INSERT INTO material_index_jobs (id,material_id,status,attempt_count,next_attempt_at,created_at,updated_at)
SELECT UUID_TO_BIN(UUID()),m.id,'queued',0,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6)
FROM materials m WHERE NOT EXISTS (SELECT 1 FROM material_index_jobs j WHERE j.material_id=m.id);
