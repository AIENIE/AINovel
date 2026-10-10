package com.ainovel.app.material.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** All persisted positions count Unicode code points in the unchanged source text. */
public final class EvidenceText {
    public record Fragment(int seq, int start, int end, String text) {}

    private EvidenceText() {}

    public static List<Fragment> split(String text) {
        int length = text.codePointCount(0, text.length());
        if (length > 400_000) throw new IllegalArgumentException("MATERIAL_TEXT_TOO_LARGE");
        List<Fragment> result = new ArrayList<>();
        for (int start = 0, seq = 0; start < length; seq++) {
            int end = Math.min(length, start + 900);
            result.add(new Fragment(seq, start, end, slice(text, start, end)));
            if (end == length) break;
            start = end - 120;
        }
        return result;
    }

    public static String slice(String text, int start, int end) {
        if (start < 0 || end < start || end > text.codePointCount(0, text.length()))
            throw new IllegalArgumentException("EVIDENCE_RANGE_INVALID");
        return text.substring(text.offsetByCodePoints(0, start), text.offsetByCodePoints(0, end));
    }

    public static String hash(String text) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String normalize(String text) {
        return text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\p{Z}\\t]+", " ")
                .strip();
    }

    public static Set<String> shingles(String text) {
        int[] points = text.codePoints().toArray();
        Set<String> result = new HashSet<>();
        for (int i = 0; i + 24 <= points.length; i += 6) result.add(new String(points, i, 24));
        return result;
    }
}
