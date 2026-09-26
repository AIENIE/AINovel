package com.ainovel.app.quality;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;

/** Offline research adapter: calls the shipping detector without Spring, DB or network. */
public class SlopStudy {
    public static void main(String[] args) throws Exception {
        ObjectMapper json = new ObjectMapper();
        SlopPatternRegistry registry = new SlopPatternRegistry();
        LocalSlopHeuristics detector = new LocalSlopHeuristics(registry);
        SlopPatternMatcher matcher = new SlopPatternMatcher(registry);
        var input = json.readTree(Files.readString(Path.of(args[0])));
        List<Object> output = new ArrayList<>();
        for (var row : input) {
            String text = row.path("text").asText();
            var context = new SlopHeuristicInput(text, "", row.path("genre").asText(),
                    row.path("tone").asText(), "", "", row.path("characterContext").asText(), "");
            var result = detector.evaluate(context);
            var hits = matcher.match(context.text(), registry.activeRules()).stream()
                    .map(h -> Map.of("id", h.rule().id(), "category", h.rule().category(),
                            "start", h.start(), "end", h.end(), "quote", h.evidence())).toList();
            output.add(Map.of("id", row.path("id").asText(), "result", result, "rawHits", hits));
        }
        Files.writeString(Path.of(args[1]), json.writerWithDefaultPrettyPrinter().writeValueAsString(output));
        System.out.println("Analyzed " + output.size() + " samples with shipping LocalSlopHeuristics; no network.");
    }
}
