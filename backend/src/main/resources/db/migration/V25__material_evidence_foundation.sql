CREATE TABLE material_revisions (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 material_id BINARY(16) NOT NULL, content_version BIGINT NOT NULL,
 owner_id BINARY(16), title VARCHAR(255) NOT NULL, content LONGTEXT NOT NULL,
 tags_json LONGTEXT NOT NULL, content_hash CHAR(64) NOT NULL, created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_material_revision (material_id, content_version)
) ENGINE=InnoDB;
CREATE TABLE material_evidence_chunks (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 material_id BINARY(16) NOT NULL, seq INT NOT NULL,
 start_cp INT NOT NULL, end_cp INT NOT NULL, title VARCHAR(255) NOT NULL, text TEXT NOT NULL,
 INDEX ix_evidence_revision (revision_id,seq),
 FULLTEXT KEY ft_evidence_text (title,text) WITH PARSER ngram,
 FOREIGN KEY (revision_id) REFERENCES material_revisions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE material_work_settings (
 story_id BINARY(16) PRIMARY KEY, row_version BIGINT NOT NULL DEFAULT 0,
 semantic_profile VARCHAR(80) NOT NULL DEFAULT 'basic', rerank_enabled BOOLEAN NOT NULL DEFAULT FALSE,
 hints_enabled BOOLEAN NOT NULL DEFAULT FALSE, checks_enabled BOOLEAN NOT NULL DEFAULT FALSE
) ENGINE=InnoDB;
CREATE TABLE material_work_bindings (
 story_id BINARY(16) NOT NULL, material_id BINARY(16) NOT NULL,
 PRIMARY KEY (story_id,material_id)
) ENGINE=InnoDB;
CREATE TABLE material_mutation_receipts (
 owner_id BINARY(16) NOT NULL, scope VARCHAR(160) NOT NULL, request_key VARCHAR(80) NOT NULL,
 request_hash CHAR(64) NOT NULL, result_json LONGTEXT NOT NULL,
 PRIMARY KEY (owner_id,scope,request_key)
) ENGINE=InnoDB;
CREATE TABLE material_merge_sources (
 merged_revision CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 source_revision CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 PRIMARY KEY(merged_revision,source_revision),
 FOREIGN KEY(merged_revision) REFERENCES material_revisions(id), FOREIGN KEY(source_revision) REFERENCES material_revisions(id)
) ENGINE=InnoDB;
CREATE TABLE material_entities (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id BINARY(16) NOT NULL, name VARCHAR(255) NOT NULL, row_version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;
CREATE TABLE material_entity_names (
 entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, name VARCHAR(255) NOT NULL,
 PRIMARY KEY (entity_id,name), FOREIGN KEY (entity_id) REFERENCES material_entities(id)
) ENGINE=InnoDB;
CREATE TABLE material_entity_sources (
 entity_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL, material_id BINARY(16) NOT NULL,
 PRIMARY KEY (entity_id,material_id), FOREIGN KEY (entity_id) REFERENCES material_entities(id)
) ENGINE=InnoDB;
CREATE TABLE material_scene_packages (
 manuscript_id BINARY(16) NOT NULL, scene_id VARCHAR(100) NOT NULL,
 row_version BIGINT NOT NULL DEFAULT 0, pinned_json LONGTEXT NOT NULL, excluded_json LONGTEXT NOT NULL,
 PRIMARY KEY (manuscript_id,scene_id)
) ENGINE=InnoDB;
CREATE TABLE material_source_links (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id BINARY(16) NOT NULL, manuscript_id BINARY(16) NOT NULL, scene_id VARCHAR(100) NOT NULL,
 branch_id CHAR(36) NOT NULL, body_version CHAR(36) NOT NULL,
 revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 relation_type VARCHAR(24) NOT NULL, start_cp INT NOT NULL, end_cp INT NOT NULL,
 quote TEXT NOT NULL, body_quote TEXT NOT NULL, state VARCHAR(24) NOT NULL DEFAULT 'CURRENT',
 request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_source_link_intent (owner_id,request_key), INDEX ix_source_links_manuscript (manuscript_id),
 FOREIGN KEY (revision_id) REFERENCES material_revisions(id)
) ENGINE=InnoDB;
CREATE TABLE material_processing_jobs (
 id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 owner_id BINARY(16) NOT NULL, kind VARCHAR(24) NOT NULL,
 request_key VARCHAR(80) NOT NULL, request_hash CHAR(64) NOT NULL,
 source_revision CHAR(36), manuscript_id BINARY(16), branch_id CHAR(36), body_version CHAR(36),
 input_json LONGTEXT NOT NULL, result_json LONGTEXT, status VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
 error_code VARCHAR(80), lease_token CHAR(36), lease_until TIMESTAMP(6),
 call_limit INT NOT NULL, calls_reserved INT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 UNIQUE KEY uq_material_job_intent (owner_id,kind,request_key), INDEX ix_processing_dispatch (status,created_at)
) ENGINE=InnoDB;
CREATE TABLE material_semantic_jobs (
 revision_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 profile VARCHAR(80) NOT NULL, model VARCHAR(100) NOT NULL, dimensions INT NOT NULL,
 template_version VARCHAR(32) NOT NULL, chunk_version VARCHAR(32) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'QUEUED', error_code VARCHAR(80), lease_token CHAR(36), lease_until TIMESTAMP(6),
 PRIMARY KEY (revision_id,profile), FOREIGN KEY (revision_id) REFERENCES material_revisions(id)
) ENGINE=InnoDB;
CREATE TABLE material_auto_check_quota (
 owner_id BINARY(16) NOT NULL, quota_day DATE NOT NULL, used_count INT NOT NULL DEFAULT 0,
 PRIMARY KEY(owner_id,quota_day)
) ENGINE=InnoDB;
-- Only raw history is backfilled. No confirmation or AI inference is synthesized.
INSERT INTO material_revisions(id,material_id,content_version,owner_id,title,content,tags_json,content_hash)
 SELECT UUID(),id,content_version,user_id,COALESCE(title,''),COALESCE(content,''),COALESCE(tags_json,'[]'),SHA2(COALESCE(content,''),256) FROM materials;
