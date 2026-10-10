package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class MaterialSelectionCollectorTest {
    @TempDir Path directory;
    final ObjectMapper json = new ObjectMapper();

    Hit hit(String id) {
        return new Hit(id, UUID.randomUUID(), "r", 1, id, id, 0, id.length(), List.of("test"), 0);
    }

    com.fasterxml.jackson.databind.JsonNode manifest() throws Exception {
        return json.readTree(
                "{\"configuration\":{\"candidateLimit\":40},\"sha256\":{\"corpus.json\":\"01234567890123456789\"}}");
    }

    @Test
    void fiveArmsShareTwoEmbeddingsAndStopReplayingCompletedCheckpoint() throws Exception {
        var calls = new ArrayList<String>();
        var ports =
                new MaterialSelectionCollector.Ports() {
                    public MaterialEvidenceService.BasicRecall basic(
                            MaterialSelectionCollector.Query q) {
                        calls.add("basic");
                        return new MaterialEvidenceService.BasicRecall(
                                "fact", "bound", List.of(hit("a")), List.of(hit("b")));
                    }

                    public List<Hit> semantic(
                            MaterialSelectionCollector.Query q, String p, String id) {
                        calls.add(id);
                        return List.of(hit("c"));
                    }

                    public List<Hit> rerank(
                            MaterialSelectionCollector.Query q, List<Hit> candidates, String id) {
                        calls.add(id);
                        var r = new ArrayList<>(candidates);
                        Collections.reverse(r);
                        return r;
                    }
                };
        var queries = List.of(new MaterialSelectionCollector.Query("q1", "fact", "日落后能否过桥"));
        Path output = directory.resolve("rankings.json");
        new MaterialSelectionCollector(json, ports, output, manifest(), "test-run")
                .collect(queries, false);
        assertEquals(5, calls.size());
        var rows = json.readTree(output.toFile()).path("rows");
        assertEquals(5, rows.size());
        assertEquals(4, calls.stream().filter(v -> v.startsWith("selection:")).distinct().count());
        new MaterialSelectionCollector(json, ports, output, manifest(), "test-run")
                .collect(queries, false);
        assertEquals(5, calls.size());
    }

    @Test
    void unknownResultPersistsPartialRowsAndPreventsAnotherArmOrRestart() throws Exception {
        var ports =
                new MaterialSelectionCollector.Ports() {
                    public MaterialEvidenceService.BasicRecall basic(
                            MaterialSelectionCollector.Query q) {
                        return new MaterialEvidenceService.BasicRecall(
                                "fact", "bound", List.of(), List.of());
                    }

                    public List<Hit> semantic(
                            MaterialSelectionCollector.Query q, String p, String id) {
                        assertEquals(MaterialSelectionCollector.PROFILES.getFirst(), p);
                        throw new IllegalStateException("result unknown");
                    }

                    public List<Hit> rerank(
                            MaterialSelectionCollector.Query q, List<Hit> c, String id) {
                        fail("No rerank after unknown result");
                        return c;
                    }
                };
        Path output = directory.resolve("rankings.json");
        assertThrows(
                IllegalStateException.class,
                () ->
                        new MaterialSelectionCollector(json, ports, output, manifest(), "test-run")
                                .collect(
                                        List.of(
                                                new MaterialSelectionCollector.Query(
                                                        "q1", "fact", "桥")),
                                        false));
        assertEquals(2, json.readTree(output.toFile()).path("rows").size());
        assertThrows(
                IllegalStateException.class,
                () -> new MaterialSelectionCollector(json, ports, output, manifest(), "test-run"));
    }
}
