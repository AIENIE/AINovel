package com.ainovel.app.admin;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Read-model queries deliberately select no entity, manuscript text or diagnostic JSON. */
@Repository
public class AdminOperationsReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminOperationsReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record AssetRow(String type, UUID id, String title, String status, String owner, Instant updatedAt) { }
    public record QualityRow(String kind, UUID id, UUID storyId, UUID manuscriptId, UUID sceneId,
                             String chapterTitle, String sceneTitle, String status, String maxSeverity,
                             int overallRiskScore, boolean resolved, String summary, Instant createdAt) { }
    public record Result<T>(List<T> items, long total) { }

    public Result<AssetRow> assets(String kind, int offset, int size, String search) {
        String select = switch (kind) {
            case "stories" -> "select 'story' as type, a.id, a.title, a.status, u.username as owner, a.updated_at from stories a left join users u on a.user_id=u.id";
            case "worlds" -> "select 'world' as type, a.id, a.name as title, a.status, u.username as owner, a.updated_at from worlds a left join users u on a.user_id=u.id";
            case "manuscripts" -> "select 'manuscript' as type, a.id, a.title, 'active' as status, u.username as owner, a.updated_at from manuscripts a left join outlines o on a.outline_id=o.id left join stories s on o.story_id=s.id left join users u on s.user_id=u.id";
            default -> throw new IllegalArgumentException("Unsupported asset kind");
        };
        String from = " from (" + select + ") a where (:emptySearch = true or lower(coalesce(title,'')) like :search escape '!' or lower(coalesce(owner,'')) like :search escape '!' or lower(coalesce(status,'')) like :search escape '!' or id = :searchId)";
        MapSqlParameterSource params = params(search, offset, size);
        long total = jdbc.queryForObject("select count(*)" + from, params, Long.class);
        List<AssetRow> rows = jdbc.query("select type,id,title,status,owner,updated_at" + from
                + " order by updated_at desc, id desc limit :size offset :offset", params,
                (rs, index) -> new AssetRow(rs.getString("type"), uuid(rs, "id"), fallback(rs.getString("title"), "(未命名)"),
                        fallback(rs.getString("status"), "unknown"), fallback(rs.getString("owner"), ""), instant(rs, "updated_at")));
        return new Result<>(rows, total);
    }

    public Result<QualityRow> quality(int offset, int size, String search, String filter) {
        // Apply one global ORDER/LIMIT to both kinds so pages cannot omit interleaved records.
        String union = "select 'slop' as kind,id,story_id,manuscript_id,scene_id,null as chapter_title,null as scene_title,status,max_severity,overall_risk_score,revised as resolved,summary,created_at from slop_quality_runs"
                + " union all select 'plot' as kind,id,story_id,manuscript_id,scene_id,chapter_title,scene_title,status,max_severity,overall_risk_score,revision_applied as resolved,summary,created_at from plot_quality_runs";
        String from = " from (" + union + ") q where (:filter = 'all' or (:filter = 'open' and resolved = false) or (:filter = 'high' and (overall_risk_score >= 70 or max_severity in ('BLOCKING','HIGH'))))"
                + " and (:emptySearch = true or lower(coalesce(chapter_title,'')) like :search escape '!' or lower(coalesce(scene_title,'')) like :search escape '!' or lower(coalesce(summary,'')) like :search escape '!' or id = :searchId or manuscript_id = :searchId or scene_id = :searchId)";
        MapSqlParameterSource params = params(search, offset, size).addValue("filter", filter);
        long total = jdbc.queryForObject("select count(*)" + from, params, Long.class);
        List<QualityRow> rows = jdbc.query("select kind,id,story_id,manuscript_id,scene_id,chapter_title,scene_title,status,max_severity,overall_risk_score,resolved,summary,created_at" + from
                + " order by created_at desc, id desc, kind desc limit :size offset :offset", params, QUALITY_ROW);
        return new Result<>(rows, total);
    }

    private static MapSqlParameterSource params(String search, int offset, int size) {
        String normalized = search.strip().toLowerCase(Locale.ROOT);
        byte[] id = null;
        try {
            UUID parsed = UUID.fromString(normalized);
            id = ByteBuffer.allocate(16).putLong(parsed.getMostSignificantBits()).putLong(parsed.getLeastSignificantBits()).array();
        } catch (IllegalArgumentException ignored) { /* Text searches need no UUID predicate. */ }
        return new MapSqlParameterSource().addValue("emptySearch", normalized.isEmpty())
                .addValue("search", "%" + normalized.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
                .addValue("searchId", id, java.sql.Types.BINARY).addValue("offset", offset).addValue("size", size);
    }

    private static final RowMapper<QualityRow> QUALITY_ROW = (rs, index) -> new QualityRow(
            rs.getString("kind"), uuid(rs, "id"), uuid(rs, "story_id"), uuid(rs, "manuscript_id"), uuid(rs, "scene_id"),
            rs.getString("chapter_title"), rs.getString("scene_title"), rs.getString("status"), rs.getString("max_severity"),
            rs.getInt("overall_risk_score"), rs.getBoolean("resolved"), rs.getString("summary"), instant(rs, "created_at"));

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        if (value == null) return null;
        if (value instanceof UUID id) return id;
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        return UUID.fromString(value.toString());
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column, java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
        return value == null ? null : value.toInstant();
    }

    private static String fallback(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
}
