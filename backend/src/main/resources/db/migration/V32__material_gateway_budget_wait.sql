ALTER TABLE material_semantic_jobs
 ADD COLUMN budget_waits INT NOT NULL DEFAULT 0,
 ADD COLUMN not_before TIMESTAMP(6) NULL;
ALTER TABLE material_processing_jobs
 ADD COLUMN budget_waits INT NOT NULL DEFAULT 0,
 ADD COLUMN not_before TIMESTAMP(6) NULL;
