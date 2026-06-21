package com.ainovel.app.quality;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;

@Component
public class SlopReviewSampleSchemaInitializer implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public SlopReviewSampleSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS slop_review_samples (
                  id binary(16) NOT NULL,
                  source_type varchar(32) NOT NULL,
                  source_run_id binary(16) DEFAULT NULL,
                  story_id binary(16) DEFAULT NULL,
                  manuscript_id binary(16) DEFAULT NULL,
                  scene_id binary(16) DEFAULT NULL,
                  sample_id varchar(120) DEFAULT NULL,
                  text longtext NOT NULL,
                  genre varchar(120) DEFAULT NULL,
                  tone varchar(120) DEFAULT NULL,
                  chapter_title varchar(200) DEFAULT NULL,
                  scene_title varchar(200) DEFAULT NULL,
                  character_context longtext,
                  style_context longtext,
                  expected_evidence_level varchar(8) NOT NULL,
                  expected_requires_ai_review boolean NOT NULL,
                  observed_evidence_level varchar(8) NOT NULL,
                  observed_requires_ai_review boolean NOT NULL,
                  observed_risk_score int NOT NULL,
                  observed_max_severity varchar(32) NOT NULL,
                  matches_expected boolean NOT NULL,
                  status varchar(32) NOT NULL,
                  reviewer_note varchar(1000) DEFAULT NULL,
                  created_by varchar(120) DEFAULT NULL,
                  reviewed_by varchar(120) DEFAULT NULL,
                  reviewed_at datetime(6) DEFAULT NULL,
                  created_at datetime(6) DEFAULT NULL,
                  updated_at datetime(6) DEFAULT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_slop_review_source_run (source_type, source_run_id),
                  KEY idx_slop_review_status (status),
                  KEY idx_slop_review_source_run (source_type, source_run_id),
                  KEY idx_slop_review_evidence (expected_evidence_level)
                )
                """);
        createMissingPlotQualityTables();
        addMissingQualityRunColumns();
    }

    private void createMissingPlotQualityTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plot_quality_runs (
                  id binary(16) NOT NULL,
                  story_id binary(16) NOT NULL,
                  manuscript_id binary(16) NOT NULL,
                  scene_id binary(16) NOT NULL,
                  chapter_title varchar(200) DEFAULT NULL,
                  scene_title varchar(200) DEFAULT NULL,
                  chapter_order int NOT NULL DEFAULT 0,
                  scene_order int NOT NULL DEFAULT 0,
                  status varchar(32) NOT NULL,
                  max_severity varchar(32) NOT NULL,
                  overall_risk_score int NOT NULL,
                  source_text_hash varchar(64) DEFAULT NULL,
                  summary varchar(800) DEFAULT NULL,
                  rewrite_plan_json longtext,
                  surgical_fixes_json longtext,
                  revision_candidate_text longtext,
                  revision_applied boolean NOT NULL,
                  revision_applied_at datetime(6) DEFAULT NULL,
                  created_at datetime(6) DEFAULT NULL,
                  updated_at datetime(6) DEFAULT NULL,
                  PRIMARY KEY (id),
                  KEY idx_plot_run_story (story_id),
                  KEY idx_plot_run_manuscript (manuscript_id),
                  KEY idx_plot_run_scene (scene_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS plot_quality_issues (
                  id binary(16) NOT NULL,
                  run_id binary(16) NOT NULL,
                  dimension varchar(40) NOT NULL,
                  severity varchar(32) NOT NULL,
                  risk_score int NOT NULL,
                  evidence varchar(800) DEFAULT NULL,
                  why_it_matters varchar(800) DEFAULT NULL,
                  minimal_fix varchar(800) DEFAULT NULL,
                  created_at datetime(6) DEFAULT NULL,
                  PRIMARY KEY (id),
                  KEY idx_plot_issue_run (run_id),
                  KEY idx_plot_issue_dimension (dimension),
                  CONSTRAINT fk_plot_issue_run FOREIGN KEY (run_id) REFERENCES plot_quality_runs (id) ON DELETE CASCADE
                )
                """);
    }

    private void addMissingQualityRunColumns() {
        if (!tableExists("slop_quality_runs")) {
            return;
        }
        addColumnIfMissing("slop_quality_runs", "candidate_text_hash", "varchar(64) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "accepted_text_hash", "varchar(64) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "source_text_hash", "varchar(64) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "analysis_mode", "varchar(40) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "risk_label", "varchar(32) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "evidence_level", "varchar(8) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "safe_claim", "varchar(500) DEFAULT NULL");
        addColumnIfMissing("slop_quality_runs", "module_scores_json", "longtext");
        addColumnIfMissing("slop_quality_runs", "alternative_explanations_json", "longtext");
        addColumnIfMissing("slop_quality_runs", "revision_priorities_json", "longtext");
        addColumnIfMissing("slop_quality_runs", "rewrite_tasks_json", "longtext");
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (!columnExists(table, column)) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private boolean tableExists(String table) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, table, null)) {
                if (result.next()) {
                    return true;
                }
            }
            try (ResultSet result = connection.getMetaData().getTables(connection.getCatalog(), null, table.toUpperCase(), null)) {
                return result.next();
            }
        }));
    }

    private boolean columnExists(String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
                if (result.next()) {
                    return true;
                }
            }
            try (ResultSet result = connection.getMetaData().getColumns(connection.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
                return result.next();
            }
        }));
    }
}
