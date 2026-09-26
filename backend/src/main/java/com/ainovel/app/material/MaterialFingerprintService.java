package com.ainovel.app.material;

import com.ainovel.app.material.model.Material;
import com.fasterxml.jackson.core.type.TypeReference;
import com.ainovel.app.common.JsonColumnCodec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** A fixed-size, versioned lexical fingerprint; no pairwise full-text reads. */
@Service
public class MaterialFingerprintService {
    private final JdbcTemplate jdbc;
    private final JsonColumnCodec json;
    public MaterialFingerprintService(JdbcTemplate jdbc, JsonColumnCodec json) { this.jdbc = jdbc; this.json = json; }
    public static byte[] bytes(UUID id) { return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array(); }
    public static UUID uuid(byte[] bytes) { var b = ByteBuffer.wrap(bytes); return new UUID(b.getLong(), b.getLong()); }
    public static Set<String> terms(String text) {
        return termsForFields(text);
    }
    private static Set<String> termsForFields(String... fields) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            // Keep the 64 smallest hashes; memory does not grow with document length.
            var result = new TreeSet<String>();
            for (String field : fields) {
                if (field == null) continue;
                for (int offset = 0; offset < field.length();) {
                    int current = field.codePointAt(offset);
                    if (!Character.isWhitespace(current)) {
                        int[] gram = new int[3];
                        int length = 0;
                        for (int next = offset; next < field.length() && length < 3;) {
                            int cp = field.codePointAt(next);
                            gram[length++] = Character.toLowerCase(cp);
                            next += Character.charCount(cp);
                        }
                        result.add(HexFormat.of().formatHex(digest.digest(new String(gram, 0, length)
                                .getBytes(StandardCharsets.UTF_8)), 0, 8));
                        if (result.size() > 64) result.pollLast();
                    }
                    offset += Character.charCount(current);
                }
            }
            return result;
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @Transactional
    public void update(Material m) {
        byte[] id = bytes(m.getId());
        jdbc.update("delete from material_duplicate_terms where material_id=?", id);
        jdbc.update("delete from material_duplicate_fingerprints where material_id=?", id);
        if ("rejected".equalsIgnoreCase(m.getStatus())) return;
        Set<String> terms = termsForFields(m.getTitle(), m.getSummary(), m.getTagsJson(), m.getContent());
        jdbc.update("insert into material_duplicate_fingerprints(material_id,content_version,title,terms_json) values(?,?,?,?)",
                id, m.getContentVersion(), Objects.toString(m.getTitle(), ""), json.writeRequired(terms));
        for (String term : terms) jdbc.update("insert into material_duplicate_terms(term,material_id,content_version) values(?,?,?)", term, id, m.getContentVersion());
    }
    Set<String> decode(String value) { return json.readRequired(value, new TypeReference<Set<String>>() {}); }
}
