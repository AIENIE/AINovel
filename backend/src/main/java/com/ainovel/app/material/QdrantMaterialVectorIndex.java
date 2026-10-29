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

    @org.springframework.beans.factory.annotation.Autowired
    public QdrantMaterialVectorIndex(
            ObjectMapper objectMapper,
            @Value("${qdrant.host:http://localqdrant.testhut.top}") String host,
            @Value("${qdrant.http-port:26333}") int port,
            @Value("${qdrant.material-collection:ainovel_material_chunks}") String collection,
            @Value("${qdrant.api-key:}") String apiKey,
            @Value("${qdrant.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${qdrant.request-timeout-ms:5000}") long requestTimeoutMs
    ) {
        this.objectMapper = objectMapper;
        String normalized = host == null || host.isBlank() ? "http://localqdrant.testhut.top" : host.trim();
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

    public void upsert(MaterialChunk chunk, float[] vector) {
        if (chunk == null || vector == null || vector.length == 0) {
            throw new IllegalArgumentException("VECTOR_INPUT_INVALID");
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

    public List<VectorMatch> searchWithin(float[] vector,int limit,java.util.UUID ownerUserId,List<java.util.UUID> allowedMaterials){
        if(allowedMaterials.isEmpty())return List.of();
        if (vector == null || vector.length == 0) {
            throw new IllegalArgumentException("VECTOR_INPUT_INVALID");
        }
        ensureCollection(vector.length);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("query", floats(vector));
            body.put("limit", Math.max(1, Math.min(limit, 50)));
            body.put("with_payload", true);
            List<Object> should = new ArrayList<>();
            should.add(Map.of("key", "ownerUserId", "match", Map.of("value", "")));
            if (ownerUserId != null) should.add(Map.of("key", "ownerUserId", "match", Map.of("value", ownerUserId.toString())));
            List<Object> must=new ArrayList<>();must.add(Map.of("key","status","match",Map.of("value","approved")));
            must.add(Map.of("key","materialId","match",Map.of("any",allowedMaterials.stream().map(java.util.UUID::toString).toList())));
            body.put("filter", Map.of("must",must,"should",should));
            JsonNode root = send("POST", "/collections/" + collection + "/points/query", body);
            List<VectorMatch> matches = new ArrayList<>();
            JsonNode points = root.path("result").path("points");
            if (!points.isArray()) throw new IllegalStateException("VECTOR_RESULT_INVALID");
            for (JsonNode item : points) {
                JsonNode payload = item.path("payload");
                if (!payload.path("chunkId").isTextual() || payload.path("chunkId").asText().isBlank()
                        || !item.path("score").isNumber() || !Double.isFinite(item.path("score").doubleValue())
                        || !payload.path("materialId").isTextual() || !payload.path("title").isTextual()
                        || !payload.path("text").isTextual() || !payload.path("chunkSeq").canConvertToInt()
                        || !payload.path("chunkSeq").isIntegralNumber())
                    throw new IllegalStateException("VECTOR_RESULT_INVALID");
                java.util.UUID materialId = java.util.UUID.fromString(payload.path("materialId").textValue());
                matches.add(new VectorMatch(payload.path("chunkId").textValue(), item.path("score").doubleValue(),
                        materialId, payload.path("title").textValue(), payload.path("text").textValue(), payload.path("chunkSeq").intValue()));
            }
            return matches;
    }

    private void ensureCollection(int dimensions) {
        if (dimensions <= 0 || ensuredDimensions.get() == dimensions) {
            return;
        }
        if (collectionExists()) {
            var root=send("GET","/collections/"+collection,null);
            int actual=root.path("result").path("config").path("params").path("vectors").path("size").asInt(-1);
            if(actual!=dimensions)throw new IllegalStateException("VECTOR_INDEX_DIMENSIONS_MISMATCH");
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
                    .method(method, body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
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
