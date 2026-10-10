-- A returned provider result is durable before credit settlement and evidence validation.
-- Recovery reuses the original frozen input and gateway request id.
ALTER TABLE material_processing_jobs ADD COLUMN provider_response_json LONGTEXT NULL;
