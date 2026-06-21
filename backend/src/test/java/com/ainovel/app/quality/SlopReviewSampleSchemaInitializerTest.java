package com.ainovel.app.quality;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlopReviewSampleSchemaInitializerTest {

    @Test
    void shouldCreateReviewSampleTableIfMissing() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SlopReviewSampleSchemaInitializer initializer = new SlopReviewSampleSchemaInitializer(jdbcTemplate);

        initializer.run();

        verify(jdbcTemplate).execute(contains("CREATE TABLE IF NOT EXISTS slop_review_samples"));
        verify(jdbcTemplate).execute(contains("uk_slop_review_source_run"));
    }

    @Test
    void shouldAddMissingPhase5ColumnsToExistingQualityRunTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:slop_schema_upgrade;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE slop_quality_runs (
                  id binary(16) NOT NULL,
                  story_id binary(16) NOT NULL,
                  manuscript_id binary(16) NOT NULL,
                  scene_id binary(16) NOT NULL,
                  status varchar(32) NOT NULL,
                  max_severity varchar(32) NOT NULL,
                  overall_risk_score int NOT NULL,
                  revised boolean NOT NULL,
                  revision_count int NOT NULL DEFAULT 0,
                  created_at datetime(6) DEFAULT NULL,
                  updated_at datetime(6) DEFAULT NULL,
                  PRIMARY KEY (id)
                )
                """);

        new SlopReviewSampleSchemaInitializer(jdbcTemplate).run();

        Integer count = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'SLOP_QUALITY_RUNS' and column_name = 'ALTERNATIVE_EXPLANATIONS_JSON'
                """, Integer.class);
        assertEquals(1, count);
    }

    @Test
    void shouldCreateMissingPlotQualityTablesForAdminQualityPage() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:plot_quality_schema;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        new SlopReviewSampleSchemaInitializer(jdbcTemplate).run();

        Integer runTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'PLOT_QUALITY_RUNS'
                """, Integer.class);
        Integer issueTableCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.tables
                where table_name = 'PLOT_QUALITY_ISSUES'
                """, Integer.class);
        assertEquals(1, runTableCount);
        assertEquals(1, issueTableCount);
    }
}
