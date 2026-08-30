package com.ainovel.app.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QdrantMaterialVectorIndex implements MaterialVectorIndex {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String collection;
    private final String apiKey;
    private final Duration requestTimeout;
    private final AtomicInteger ensuredDimensions = new AtomicInteger(0);

    public QdrantMaterialVectorIndex(
            ObjectMapper objectMapper,
            @Value("${qdrant.host:http://localbase.testhut.top}") String host,
            @Value("${qdrant.http-port:26333}") int port,
            @Value("${qdrant.material-collection:ainovel_material_chunks}") String collection,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${qdrant.request-timeout-ms:5000}") long requestTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        String normalized = host == null || host.isBlank() ? "http://localbase.testhut.top" : host.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.matches("https?://.*:\\d+$")) {
            normalized = normalized + ":" + port;
        }
        this.baseUrl = normalized;
        this.collection = collection == null || collection.isBlank() ? "ainovel_material_chunks" : collection.trim();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.requestTimeout = Duration.ofMillis(Math.max(500L, requestTimeoutMs));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(500L, connectTimeoutMs)))
                .build();
    }

    @Override
    public void upsert(MaterialChunk chunk, float[] vector) {
        if (chunk == null || vector == null || vector.length == 0) {
            return;
        }
        ensureCollection(vector.length);
        Map<String, Object> body = Map.of(
                    "points", List.of(Map.of(
                            "id", chunk.chunkId(),
                            "vector", floats(vector),
                            "payload", Map.of(
                                    "chunkId", chunk.chunkId(),
                                    "materialId", chunk.materialId().toString(),
                                    "title", chunk.title() == null ? "" : chunk.title(),
                                    "chunkSeq", chunk.chunkSeq(),
                                    "text", chunk.text(),
                                    "ownerUserId", chunk.ownerUserId() == null ? "" : chunk.ownerUserId().toString(),
                                    "status", chunk.status() == null ? "" : chunk.status()
                            )
                    ))
            );
        send("PUT", "/collections/" + collection + "/points?wait=true", body);
    }

    @Override
    public void deleteMaterial(java.util.UUID materialId) {
        if (materialId == null) {
            return;
        }
        Map<String, Object> selector = Map.of(
                    "filter", Map.of("must", List.of(
                            Map.of("key", "materialId", "match", Map.of("value", materialId.toString()))
                    ))
            );
        send("POST", "/collections/" + collection + "/points/delete?wait=true", selector);
    }

    @Override
    public List<VectorMatch> search(float[] vector, int limit, java.util.UUID ownerUserId) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        ensureCollection(vector.length);
        try {
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("vector", floats(vector));
            body.put("limit", Math.max(1, Math.min(limit, 50)));
            body.put("with_payload", true);
            List<Object> should = new ArrayList<>();
            should.add(Map.of("key", "ownerUserId", "match", Map.of("value", "")));
            if (ownerUserId != null) should.add(Map.of("key", "ownerUserId", "match", Map.of("value", ownerUserId.toString())));
            body.put("filter", Map.of("must", List.of(Map.of("key", "status", "match", Map.of("value", "approved"))), "should", should));
            JsonNode root = send("POST", "/collections/" + collection + "/points/search", body);
            List<VectorMatch> matches = new ArrayList<>();
            for (JsonNode item : root.path("result")) {
                String chunkId = item.path("payload").path("chunkId").asText("");
                double score = item.path("score").asDouble(0d);
                if (!chunkId.isBlank()) {
                    JsonNode payload = item.path("payload");
                    java.util.UUID materialId = java.util.UUID.fromString(payload.path("materialId").asText());
                    matches.add(new VectorMatch(chunkId, score, materialId, payload.path("title").asText("素材"),
                            payload.path("text").asText(""), payload.path("chunkSeq").asInt(0)));
                }
            }
            return matches;
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private void ensureCollection(int dimensions) {
        if (dimensions <= 0 || ensuredDimensions.get() == dimensions) {
            return;
        }
        if (collectionExists()) {
            ensuredDimensions.set(dimensions);
            return;
        }
        Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", dimensions,
                            "distance", "Cosine"
                    )
            );
        send("PUT", "/collections/" + collection, body);
        ensuredDimensions.set(dimensions);
    }

    private boolean collectionExists() {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/collections/" + collection))
                    .timeout(requestTimeout)
                    .GET();
            if (!apiKey.isBlank()) {
                builder.header("api-key", apiKey);
            }
            HttpResponse<Void> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() == 404) {
                return false;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Qdrant returned " + response.statusCode());
            }
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant request interrupted", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant request failed", ex);
        }
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .timeout(requestTimeout);
            if (!apiKey.isBlank()) {
                builder.header("api-key", apiKey);
            }
            HttpRequest request = builder
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Qdrant returned " + response.statusCode());
            }
            return objectMapper.readTree(response.body());
        } catch (Exception ex) {
            throw new IllegalStateException("Qdrant request failed", ex);
        }
    }

    private List<Float> floats(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
