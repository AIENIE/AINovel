package com.ainovel.app.quality;

import java.util.LinkedHashMap;
import java.util.Map;

final class SlopRepetitionDetector {
    SlopIssueDraft detect(String text) {
        if (text == null || text.isBlank()) return null;
        StringBuilder compact = new StringBuilder();
        int[] originalOffsets = new int[text.length()];
        int compactIndex = 0;
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (!Character.isWhitespace(value)) {
                compact.append(value);
                originalOffsets[compactIndex++] = index;
            }
        }
        if (compact.length() <= 30) return null;
        int gramLength = 10;
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int index = 0; index + gramLength <= compact.length(); index += gramLength / 2) {
            counts.merge(compact.substring(index, index + gramLength), 1, Integer::sum);
        }
        String repeated = counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= 2)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        if (repeated == null) return null;
        int count = counts.get(repeated);
        int compactStart = compact.indexOf(repeated);
        int start = originalOffsets[compactStart];
        int end = originalOffsets[compactStart + repeated.length() - 1] + 1;
        return new SlopIssueDraft(
                SlopDimension.REPETITION,
                count >= 3 ? SlopSeverity.HIGH : SlopSeverity.MEDIUM,
                count >= 3 ? 82 : 68,
                text.substring(start, end),
                "连续复用相同表达会让场景显得原地打转。",
                "保留一次核心信息，其余改为动作、结果或删减。",
                start, end, text.substring(start, end), "surface_template",
                "SURFACE_REPETITION_NGRAM", "repetition", count >= 3 ? "E2" : "E1",
                "[\"作者刻意回环\",\"类型文强调句\",\"人工水字数\"]",
                "保留一次核心信息，其余改为动作、结果或删减。"
        );
    }
}
