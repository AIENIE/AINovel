ALTER TABLE manuscript_versions ADD COLUMN narrative_protected bit(1) NOT NULL DEFAULT b'0';

CREATE TABLE narrative_ledgers (
  branch_id binary(16) NOT NULL PRIMARY KEY,
  revision bigint NOT NULL DEFAULT 0,
  lock_version bigint NOT NULL DEFAULT 0,
  CONSTRAINT fk_narrative_ledger_branch FOREIGN KEY (branch_id) REFERENCES manuscript_branches(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE narrative_approvals (
  id binary(16) NOT NULL PRIMARY KEY,
  branch_id binary(16) NOT NULL,
  version_id binary(16) NOT NULL,
  scene_id binary(16) NOT NULL,
  confirmed_by binary(16) NOT NULL,
  idempotency_key varchar(128) COLLATE utf8mb4_bin NOT NULL,
  request_hash varchar(64) NOT NULL,
  text_hash varchar(64) NOT NULL,
  blocks_json longtext NOT NULL,
  position_json longtext NOT NULL,
  confirmed_at datetime(6) NOT NULL,
  UNIQUE KEY uk_narrative_approval_request (branch_id,idempotency_key),
  KEY idx_narrative_approval_scene (branch_id,scene_id),
  CONSTRAINT fk_narrative_approval_ledger FOREIGN KEY (branch_id) REFERENCES narrative_ledgers(branch_id) ON DELETE CASCADE,
  CONSTRAINT fk_narrative_approval_version FOREIGN KEY (version_id) REFERENCES manuscript_versions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE narrative_extractions (
  id binary(16) NOT NULL PRIMARY KEY,
  approval_id binary(16) NOT NULL,
  operation_id binary(16),
  base_canon_revision bigint NOT NULL,
  prompt_version varchar(40) NOT NULL,
  model varchar(120),
  input_json longtext NOT NULL,
  candidates_json longtext,
  usage_json longtext,
  review_json longtext,
  error varchar(80),
  created_at datetime(6) NOT NULL,
  UNIQUE KEY uk_narrative_extraction_approval (approval_id),
  CONSTRAINT fk_narrative_extraction_approval FOREIGN KEY (approval_id) REFERENCES narrative_approvals(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE narrative_records (
  id binary(16) NOT NULL PRIMARY KEY,
  extraction_id binary(16) NOT NULL,
  assertion_json longtext NOT NULL,
  dependency_ids_json longtext NOT NULL,
  created_revision bigint NOT NULL,
  invalidated_revision bigint,
  superseded_revision bigint,
  created_at datetime(6) NOT NULL,
  KEY idx_narrative_record_extraction (extraction_id),
  CONSTRAINT fk_narrative_record_extraction FOREIGN KEY (extraction_id) REFERENCES narrative_extractions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE narrative_commits (
  id binary(16) NOT NULL PRIMARY KEY,
  branch_id binary(16) NOT NULL,
  revision bigint NOT NULL,
  kind varchar(20) NOT NULL,
  idempotency_key varchar(128) COLLATE utf8mb4_bin,
  request_hash varchar(64),
  author_id binary(16),
  detail_json longtext NOT NULL,
  created_at datetime(6) NOT NULL,
  UNIQUE KEY uk_narrative_commit_revision (branch_id,revision),
  UNIQUE KEY uk_narrative_commit_request (branch_id,idempotency_key),
  CONSTRAINT fk_narrative_commit_ledger FOREIGN KEY (branch_id) REFERENCES narrative_ledgers(branch_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
