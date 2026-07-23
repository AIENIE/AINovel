package com.ainovel.app.quality;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class SlopShadowAnalyzer {
    private static final List<String> CONNECTORS = List.of(
            "首先", "其次", "最后", "此外", "同时", "因此", "然而", "总而言之", "值得注意的是"
    );
    private final SlopPatternMatcher matcher;

    SlopShadowAnalyzer(SlopPatternMatcher matcher) {
        this.matcher = matcher;
    }

    List<SlopShadowHit> analyze(String text, List<SlopPatternRule> rules) {
        List<SlopShadowHit> result = new ArrayList<>();
        for (SlopPatternRule rule : rules) {
            SlopShadowHit hit = rule.detector() == null || rule.detector().isBlank()
                    ? matched(text, rule)
                    : detected(text, rule);
            if (hit != null) result.add(hit);
        }
        return List.copyOf(result);
    }

    private SlopShadowHit matched(String text, SlopPatternRule rule) {
        List<SlopPatternHit> hits = matcher.match(text, List.of(rule));
        if (hits.isEmpty()) return null;
        SlopPatternHit first = hits.getFirst();
        return shadow(rule, hits.size(), first.start(), first.end(), first.evidence());
    }

    private SlopShadowHit detected(String text, SlopPatternRule rule) {
        return switch (rule.detector()) {
            case "RULE_OF_THREE" -> ruleOfThree(text, rule);
            case "UNIFORM_SENTENCE_LENGTH" -> uniformSentenceLength(text, rule);
            case "LOW_LEXICAL_VARIATION" -> lowLexicalVariation(text, rule);
            case "CONNECTOR_DENSITY" -> connectorDensity(text, rule);
            default -> null;
        };
    }

    private SlopShadowHit ruleOfThree(String text, SlopPatternRule rule) {
        for (List<String> sequence : List.of(
                List.of("首先", "其次", "最后"), List.of("第一", "第二", "第三"), List.of("一是", "二是", "三是"))) {
            int first = text.indexOf(sequence.get(0));
            int second = first < 0 ? -1 : text.indexOf(sequence.get(1), first + 1);
            int third = second < 0 ? -1 : text.indexOf(sequence.get(2), second + 1);
            if (third >= 0 && third - first <= 500) {
                return shadow(rule, 3, first, third + sequence.get(2).length(), text.substring(first, third + sequence.get(2).length()));
            }
        }
        return null;
    }

    private SlopShadowHit uniformSentenceLength(String text, SlopPatternRule rule) {
        String[] raw = text.split("[。！？!?]+");
        List<String> sentences = new ArrayList<>();
        for (String sentence : raw) if (!sentence.isBlank()) sentences.add(sentence.strip());
        if (sentences.size() < 5) return null;
        double average = sentences.stream().mapToInt(String::length).average().orElse(0);
        if (average < 8) return null;
        double variance = sentences.stream().mapToDouble(value -> Math.pow(value.length() - average, 2)).average().orElse(0);
        if (Math.sqrt(variance) / average > 0.12) return null;
        String evidence = sentences.getFirst();
        int start = text.indexOf(evidence);
        return shadow(rule, sentences.size(), start, start + evidence.length(), evidence);
    }

    private SlopShadowHit lowLexicalVariation(String text, SlopPatternRule rule) {
        String compact = text.replaceAll("[^\\p{IsHan}A-Za-z0-9]", "");
        if (compact.length() < 120) return null;
        Set<String> bigrams = new HashSet<>();
        for (int index = 0; index + 2 <= compact.length(); index++) {
            bigrams.add(compact.substring(index, index + 2));
        }
        double ratio = bigrams.size() / (double) (compact.length() - 1);
        if (ratio >= 0.45) return null;
        String evidence = text.substring(0, Math.min(24, text.length()));
        return shadow(rule, compact.length() - 1 - bigrams.size(), 0, evidence.length(), evidence);
    }

    private SlopShadowHit connectorDensity(String text, SlopPatternRule rule) {
        List<Integer> offsets = new ArrayList<>();
        for (String connector : CONNECTORS) {
            int from = 0;
            while (from < text.length()) {
                int at = text.indexOf(connector, from);
                if (at < 0) break;
                offsets.add(at);
                from = at + connector.length();
            }
        }
        offsets.sort(Integer::compareTo);
        int right = 0;
        for (int left = 0; left < offsets.size(); left++) {
            while (right < offsets.size() && offsets.get(right) < offsets.get(left) + 500) right++;
            if (right - left >= 6) {
                int start = offsets.get(left);
                int end = Math.min(text.length(), offsets.get(right - 1) + 6);
                return shadow(rule, right - left, start, end, text.substring(start, end));
            }
        }
        return null;
    }

    private SlopShadowHit shadow(SlopPatternRule rule, int count, int start, int end, String evidence) {
        return new SlopShadowHit(rule.id(), rule.category(), count, start, end,
                evidence.length() <= 80 ? evidence : evidence.substring(0, 80));
    }
}
