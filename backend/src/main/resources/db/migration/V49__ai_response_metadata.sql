-- Preserve safe outcome metadata with the already durable result; historical rows remain unknown.
ALTER TABLE ai_credit_reservations ADD COLUMN result_metadata_json LONGTEXT NULL;
