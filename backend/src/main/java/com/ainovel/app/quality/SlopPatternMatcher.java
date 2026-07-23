package com.ainovel.app.quality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SlopPatternMatcher {
    private final SlopPatternRegistry registry;

    SlopPatternMatcher(SlopPatternRegistry registry) {
        this.registry = registry;
    }

    List<SlopPatternHit> match(String text, List<SlopPatternRule> rules) {
        String safeText = text == null ? "" : text;
        String lower = safeText.toLowerCase(Locale.ROOT);
        List<SlopPatternHit> hits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (SlopPatternRule rule : rules) {
            String haystack = rule.ignoreCase() ? lower : safeText;
            for (String literal : rule.literals()) {
                String needle = rule.ignoreCase() ? literal.toLowerCase(Locale.ROOT) : literal;
                int from = 0;
                while (from <= haystack.length() - needle.length()) {
                    int start = haystack.indexOf(needle, from);
                    if (start < 0) break;
                    add(hits, seen, safeText, rule, start, start + needle.length());
                    from = start + Math.max(1, needle.length());
                }
            }
            for (Pattern pattern : registry.compiledRegexes(rule)) {
                Matcher matcher = pattern.matcher(safeText);
                while (matcher.find()) {
                    add(hits, seen, safeText, rule, matcher.start(), matcher.end());
                }
            }
        }
        return hits.stream()
                .sorted((left, right) -> {
                    int start = Integer.compare(left.start(), right.start());
                    return start != 0 ? start : left.rule().id().compareTo(right.rule().id());
                })
                .toList();
    }

    private void add(List<SlopPatternHit> hits,
                     Set<String> seen,
                     String text,
                     SlopPatternRule rule,
                     int start,
                     int end) {
        String key = rule.id() + ':' + start + ':' + end;
        if (end > start && seen.add(key)) {
            hits.add(new SlopPatternHit(rule, start, end, text.substring(start, end)));
        }
    }
}
