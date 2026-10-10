ALTER TABLE material_source_links
 ADD COLUMN body_block_id VARCHAR(80) NULL,
 ADD COLUMN body_start_cp INT NULL,
 ADD COLUMN body_end_cp INT NULL,
 ADD COLUMN body_conversion_version VARCHAR(80) NULL;
-- Existing relationships remain reviewable. Historical text does not fabricate a location.
UPDATE material_source_links SET state='REVIEW_REQUIRED' WHERE relation_type='CONFIRMED';
