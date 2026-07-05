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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@ConditionalOnProperty(prefix = "qdrant", name = "enabled", havingValue = "true", matchIfMissing = true)
public class QdrantMaterialVectorIndex implements MaterialVectorIndex {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    private final String baseUrl;
    private final String collection;
    private final AtomicInteger ensuredDimensions = new AtomicInteger(0);

    public QdrantMaterialVectorIndex(
            ObjectMapper objectMapper,
            @Value("${qdrant.host:http://base.seekerhut.com}") String host,
            @Value("${qdrant.http-port:26333}") int port,
            @Value("${qdrant.material-collection:ainovel_material_chunks}") String collection
    ) {
        this.objectMapper = objectMapper;
        String normalized = host == null || host.isBlank() ? "http://base.seekerhut.com" : host.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (!normalized.matches("https?://.*:\\d+$")) {
            normalized = normalized + ":" + port;
        }
        this.baseUrl = normalized;
        this.collection = collection == null || collection.isBlank() ? "ainovel_material_chunks" : collection.trim();
    }

    @Override
    public void upsert(MaterialChunk chunk, float[] vector) {
        if (chunk == null || vector == null || vector.length == 0) {
            return;
        }
        ensureCollection(vector.length);
        try {
            Map<String, Object> body = Map.of(
                    "points", List.of(Map.of(
                            "id", chunk.chunkId(),
                            "vector", floats(vector),
                            "payload", Map.of(
                                    "chunkId", chunk.chunkId(),
                                    "materialId", chunk.materialId().toString(),
                                    "title", chunk.title() == null ? "" : chunk.title(),
                                    "chunkSeq", chunk.chunkSeq()
                            )
                    ))
            );
            send("PUT", "/collections/" + collection + "/points?wait=true", body);
        } catch (RuntimeException ignored) {
            // Indexing is best effort; keyword retrieval remains available.
        }
    }

    @Override
    public List<VectorMatch> search(float[] vector, int limit) {
        if (vector == null || vector.length == 0) {
            return List.of();
        }
        ensureCollection(vector.length);
        try {
            Map<String, Object> body = Map.of(
                    "vector", floats(vector),
                    "limit", Math.max(1, Math.min(limit, 50)),
                    "with_payload", true
            );
            JsonNode root = send("POST", "/collections/" + collection + "/points/search", body);
            List<VectorMatch> matches = new ArrayList<>();
            for (JsonNode item : root.path("result")) {
                String chunkId = item.path("payload").path("chunkId").asText("");
                double score = item.path("score").asDouble(0d);
                if (!chunkId.isBlank()) {
                    matches.add(new VectorMatch(chunkId, score));
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
        try {
            Map<String, Object> body = Map.of(
                    "vectors", Map.of(
                            "size", dimensions,
                            "distance", "Cosine"
                    )
            );
            send("PUT", "/collections/" + collection, body);
            ensuredDimensions.set(dimensions);
        } catch (RuntimeException ignored) {
        }
    }

    private JsonNode send(String method, String path, Object body) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
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
