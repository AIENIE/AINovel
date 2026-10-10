package com.ainovel.app.material.evidence;

import static com.ainovel.app.material.evidence.EvidenceDtos.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.*;
import java.util.*;

/** Shared five-arm collector. Providers are injected; it never opens answer labels. */
final class MaterialSelectionCollector {
    static final List<String> PROFILES =
            List.of("qwen-standard-1024-cp-v1", "qwen-flash-1024-cp-v1");

    record Query(String id, String mode, String text) {}

    interface Ports {
        MaterialEvidenceService.BasicRecall basic(Query query);

        List<Hit> semantic(Query query, String profile, String requestId);

        List<Hit> rerank(Query query, List<Hit> candidates, String requestId);
    }

    private final ObjectMapper json;
    private final Ports ports;
    private final Path output;
    private final JsonNode configuration;
    private final JsonNode hashes;
    private final String run;
    private final java.util.function.Function<Hit, String> outputIdentity;
    private final List<Map<String, Object>> rows = new ArrayList<>();

    MaterialSelectionCollector(
            ObjectMapper json, Ports ports, Path output, JsonNode manifest, String run)
            throws Exception {
        this(json, ports, output, manifest, run, Hit::chunkId);
    }

    MaterialSelectionCollector(
            ObjectMapper json,
            Ports ports,
            Path output,
            JsonNode manifest,
            String run,
            java.util.function.Function<Hit, String> outputIdentity)
            throws Exception {
        this.json = json;
        this.ports = ports;
        this.output = output.toAbsolutePath();
        this.run = run;
        this.outputIdentity = outputIdentity;
        if (!run.matches("[A-Za-z0-9_-]{1,80}"))
            throw new IllegalArgumentException("Stable run required");
        configuration = manifest.path("configuration");
        hashes = manifest.path("sha256");
        if (Files.exists(this.output)) {
            var previous = json.readTree(this.output.toFile());
            if (!previous.path("configuration").equals(configuration)
                    || !previous.path("datasetSha256").equals(hashes)
                    || !previous.path("run").asText().equals(run))
                throw new IllegalArgumentException("Checkpoint configuration changed");
            for (var row : previous.path("rows")) {
                if (row.has("failure"))
                    throw new IllegalStateException(
                            "Failed checkpoint requires reconciliation before continuing");
                rows.add(
                        json.convertValue(
                                row,
                                new com.fasterxml.jackson.core.type.TypeReference<
                                        Map<String, Object>>() {}));
            }
        }
    }

    void collect(List<Query> queries, boolean basicOnly) throws Exception {
        for (var query : queries) {
            if (done(query.id(), "basic")
                    && (basicOnly
                            || List.of("standard", "flash", "standard-rerank", "flash-rerank")
                                    .stream()
                                    .allMatch(arm -> done(query.id(), arm)))) continue;
            long start = System.nanoTime();
            var basic = ports.basic(query);
            double basicMs = millis(start);
            var base = fused(basic.rankings());
            put(query, "basic", base, basicMs, null);
            if (basicOnly) {
                save();
                continue;
            }
            for (int profileIndex = 0; profileIndex < PROFILES.size(); profileIndex++) {
                String arm = profileIndex == 0 ? "standard" : "flash",
                        profile = PROFILES.get(profileIndex);
                if (done(query.id(), arm + "-rerank")) continue;
                start = System.nanoTime();
                try {
                    // Each query/model embedding is shared by its plain and reranked arms.
                    var semantic = ports.semantic(query, profile, requestId(query, arm + ":query"));
                    double semanticMs = millis(start);
                    var candidates = fused(List.of(basic.exact(), basic.full(), semantic));
                    put(query, arm, candidates, basicMs + semanticMs, null);
                    save();
                    start = System.nanoTime();
                    var ranked =
                            candidates.isEmpty()
                                    ? candidates
                                    : ports.rerank(
                                            query, candidates, requestId(query, arm + ":rerank"));
                    var originalIds = new HashSet<>(candidates.stream().map(Hit::chunkId).toList());
                    if (ranked.size() != candidates.size()
                            || ranked.stream().map(Hit::chunkId).distinct().count() != ranked.size()
                            || ranked.stream().anyMatch(h -> !originalIds.contains(h.chunkId())))
                        throw new IllegalStateException("Rerank changed candidate identity");
                    put(query, arm + "-rerank", ranked, basicMs + semanticMs + millis(start), null);
                    save();
                } catch (RuntimeException failure) {
                    String failedArm = done(query.id(), arm) ? arm + "-rerank" : arm;
                    put(
                            query,
                            failedArm,
                            List.of(),
                            basicMs + millis(start),
                            failure.getClass().getSimpleName());
                    save();
                    // Unknown supplier outcomes halt this run. Never continue through another arm.
                    throw new IllegalStateException(
                            "Selection stopped; original request IDs retained for reconciliation",
                            failure);
                }
            }
        }
        save();
    }

    private String requestId(Query query, String operation) {
        return "selection:"
                + EvidenceText.hash(run).substring(0, 12)
                + ":"
                + hashes.path("corpus.json").asText().substring(0, 12)
                + ":"
                + EvidenceText.hash(query.id()).substring(0, 16)
                + ":"
                + operation;
    }

    private static List<Hit> fused(List<List<Hit>> rankings) {
        return MaterialEvidenceService.fuse(rankings).stream().limit(40).toList();
    }

    private static double millis(long start) {
        return (System.nanoTime() - start) / 1_000_000d;
    }

    private boolean done(String query, String arm) {
        return rows.stream()
                .anyMatch(r -> query.equals(r.get("queryId")) && arm.equals(r.get("arm")));
    }

    private void put(Query query, String arm, List<Hit> hits, double ms, String failure) {
        rows.removeIf(row -> query.id().equals(row.get("queryId")) && arm.equals(row.get("arm")));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("queryId", query.id());
        row.put("arm", arm);
        // Preserve production UUID tie breaking. Dataset IDs are only an output mapping.
        row.put("chunks", hits.stream().map(outputIdentity).toList());
        row.put("elapsedMs", ms);
        if (failure != null) row.put("failure", failure);
        rows.add(row);
    }

    private void save() throws Exception {
        writeCheckpoint(
                json,
                output,
                Map.of(
                        "run",
                        run,
                        "configuration",
                        configuration,
                        "datasetSha256",
                        hashes,
                        "rows",
                        rows));
    }

    static void writeCheckpoint(ObjectMapper json, Path output, Object value) throws Exception {
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".partial");
        json.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
        Files.move(
                temporary,
                output,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }
}
