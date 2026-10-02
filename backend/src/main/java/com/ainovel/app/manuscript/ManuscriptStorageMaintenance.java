package com.ainovel.app.manuscript;

import com.ainovel.app.common.JsonColumnCodec;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.ainovel.app.material.MaterialFingerprintService.bytes;
import static com.ainovel.app.material.MaterialFingerprintService.uuid;

/** Standalone JDBC entry point: does not start Spring, HTTP, scheduling, or AI clients. */
public final class ManuscriptStorageMaintenance {
    private static final JsonColumnCodec JSON = new JsonColumnCodec(new ObjectMapper());
    private ManuscriptStorageMaintenance() {}
    public static void main(String[] args) throws Exception {
        com.aienie.configpair.RuntimeConfiguration.initialize(args);
        if (args.length != 2 || !"--maintenance-confirmed".equals(args[1]))
            throw new IllegalArgumentException("Usage: backfill|verify|reverse --maintenance-confirmed; stop all writers and verify backup first");
        String url = required("AINOVEL_MIGRATION_JDBC_URL");
        try (Connection connection = DriverManager.getConnection(url, required("AINOVEL_MIGRATION_USER"), required("AINOVEL_MIGRATION_PASSWORD"))) {
            int count = run(connection, args[0], Integer.MAX_VALUE);
            System.out.println("{\"action\":\"" + args[0] + "\",\"verifiedManuscripts\":" + count + "}");
        }
    }
    private static String required(String key) {
        String value = com.aienie.configpair.RuntimeConfiguration.getenv(key); if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key); return value;
    }
    public static int run(Connection connection, String action, int maxRows) throws Exception {
        if (!Set.of("backfill", "verify", "reverse").contains(action) || maxRows < 1) throw new IllegalArgumentException("Invalid maintenance action");
        boolean autoCommit = connection.getAutoCommit(); connection.setAutoCommit(false);
        byte[] cursor = new byte[16]; int processed = 0;
        try {
            while (processed < maxRows) {
                byte[] id; String archive; int storage;
                try (var query = connection.prepareStatement("select id,sections_json,content_storage_version from manuscripts where id>? order by id limit 1 for update")) {
                    query.setBytes(1, cursor);
                    try (var rows = query.executeQuery()) { if (!rows.next()) break; id = rows.getBytes(1); archive = rows.getString(2); storage = rows.getInt(3); }
                }
                if ("reverse".equals(action)) {
                    if (storage == 2) {
                        Map<String,String> current = scenes(connection, id);
                        String rebuilt = JSON.writeRequired(current);
                        if (!current.equals(JSON.readSections(rebuilt))) throw new IllegalStateException("REVERSE_SERIALIZATION_MISMATCH");
                        try (var update = connection.prepareStatement("update manuscripts set sections_json=?,content_storage_version=1,version=version+1 where id=?")) {
                            update.setString(1, rebuilt); update.setBytes(2, id); update.executeUpdate();
                        }
                        try (var q = connection.prepareStatement("select sections_json from manuscripts where id=?")) {
                            q.setBytes(1,id); try (var r=q.executeQuery()) { r.next(); if (!current.equals(JSON.readSections(r.getString(1)))) throw new IllegalStateException("REVERSE_READBACK_MISMATCH"); }
                        }
                    }
                } else {
                    Map<String,String> source = JSON.readSections(archive);
                    for (String key : source.keySet()) ManuscriptContentService.strictId(key);
                    if ("verify".equals(action) && storage != 2) throw new IllegalStateException("UNMIGRATED_MANUSCRIPT");
                    if (storage == 1 && "backfill".equals(action)) {
                        try (var delete = connection.prepareStatement("delete from manuscript_scene_contents where manuscript_id=?")) { delete.setBytes(1,id); delete.executeUpdate(); }
                        try (var insert = connection.prepareStatement("insert into manuscript_scene_contents(manuscript_id,scene_id,content,word_count,updated_at) values(?,?,?,?,CURRENT_TIMESTAMP)")) {
                            for (var entry : source.entrySet()) {
                                insert.setBytes(1,id); insert.setBytes(2,bytes(ManuscriptContentService.strictId(entry.getKey())));
                                insert.setString(3,entry.getValue()); insert.setLong(4,ManuscriptContentService.words(entry.getValue())); insert.addBatch();
                            }
                            insert.executeBatch();
                        }
                    } else if (storage != 2) throw new IllegalStateException("UNMIGRATED_MANUSCRIPT");
                    Map<String,String> target = scenes(connection,id);
                    if (!source.equals(target) || !hash(source).equals(hash(target))) throw new IllegalStateException("SCENE_BACKFILL_MISMATCH");
                    if ("verify".equals(action)) {
                        try (var q = connection.prepareStatement("select scene_count,content_hash from manuscript_content_migration where manuscript_id=?")) {
                            q.setBytes(1,id);
                            try (var row = q.executeQuery()) {
                                if (!row.next() || row.getInt(1) != target.size() || !hash(target).equals(row.getString(2)))
                                    throw new IllegalStateException("SCENE_VERIFICATION_RECORD_MISMATCH");
                            }
                        }
                    } else {
                        try (var update = connection.prepareStatement("update manuscripts set content_storage_version=2 where id=?")) { update.setBytes(1,id); update.executeUpdate(); }
                        try (var delete = connection.prepareStatement("delete from manuscript_content_migration where manuscript_id=?")) { delete.setBytes(1,id); delete.executeUpdate(); }
                        try (var insert = connection.prepareStatement("insert into manuscript_content_migration(manuscript_id,scene_count,content_hash,verified_at) values(?,?,?,CURRENT_TIMESTAMP)")) {
                            insert.setBytes(1,id); insert.setInt(2,target.size()); insert.setString(3,hash(target)); insert.executeUpdate();
                        }
                    }
                }
                connection.commit(); cursor = id;
                if ("verify".equals(action) || "backfill".equals(action) && storage == 1 || "reverse".equals(action) && storage == 2) processed++;
            }
            connection.commit(); return processed;
        } catch (Exception ex) { connection.rollback(); throw ex; }
        finally { connection.setAutoCommit(autoCommit); }
    }
    private static Map<String,String> scenes(Connection connection, byte[] id) throws SQLException {
        Map<String,String> result = new LinkedHashMap<>();
        try (var q = connection.prepareStatement("select scene_id,content,word_count from manuscript_scene_contents where manuscript_id=? order by scene_id")) {
            q.setBytes(1,id); try (var r=q.executeQuery()) { while(r.next()) {
                String content = r.getString(2); if (content == null || r.getLong(3) != ManuscriptContentService.words(content)) throw new IllegalStateException("SCENE_WORD_COUNT_MISMATCH");
                result.put(uuid(r.getBytes(1)).toString(), content);
            } }
        }
        return result;
    }
    private static String hash(Map<String,String> sections) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeRequired(new TreeMap<>(sections)).getBytes(StandardCharsets.UTF_8)));
    }
}
