package com.ainovel.app.material;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.nio.ByteBuffer;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Bounded governance list; content and tag LOBs are deliberately excluded. */
@Repository
public class MaterialAdminReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public MaterialAdminReadRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PendingMaterial(UUID id, String title, String type, String summary, Instant createdAt) { }
    public record Page(List<PendingMaterial> items, int page, int size, long totalElements, long totalPages) { }

    public Page pending(int page, int size) {
        var params = new MapSqlParameterSource().addValue("offset", page * size).addValue("size", size);
        long total = jdbc.queryForObject("select count(*) from materials where status = 'pending'", params, Long.class);
        List<PendingMaterial> items = jdbc.query("select id,title,type,summary,created_at from materials "
                        + "where status = 'pending' order by created_at asc, id asc limit :size offset :offset",
                params, (rs, row) -> new PendingMaterial(uuid(rs.getBytes("id")), rs.getString("title"),
                        rs.getString("type"), rs.getString("summary"), instant(rs.getTimestamp("created_at"))));
        return new Page(items, page, size, total, (total + size - 1) / size);
    }

    private static UUID uuid(byte[] bytes) throws SQLException {
        if (bytes == null || bytes.length != 16) throw new SQLException("Invalid material ID");
        var buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
}
