ALTER TABLE material_source_links
 ADD COLUMN report_task_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
 ADD COLUMN report_finding_index INT NULL,
 ADD INDEX ix_source_link_report (report_task_id,report_finding_index),
 ADD CONSTRAINT fk_source_link_report FOREIGN KEY (report_task_id) REFERENCES material_processing_jobs(id);
