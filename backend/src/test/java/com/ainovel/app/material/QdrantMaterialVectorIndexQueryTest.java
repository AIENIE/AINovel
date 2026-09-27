package com.ainovel.app.material;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantMaterialVectorIndexQueryTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void queriesPointsAndPreservesOwnerFilterAndMatchMapping() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        UUID materialId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/collections/materials", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 200, "{}");
                return;
            }
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(json.readTree(exchange.getRequestBody()));
            respond(exchange, 200, "{\"result\":{\"points\":[{\"score\":0.91,\"payload\":{\"chunkId\":\"chunk-1\",\"materialId\":\""
                    + materialId + "\",\"title\":\"Title\",\"text\":\"Text\",\"chunkSeq\":2}}]}}");
        });
        server.start();

        var index = new QdrantMaterialVectorIndex(json, "http://127.0.0.1", server.getAddress().getPort(),
                "materials", "", 1000, 1000);
        var matches = index.search(new float[]{0.1f, 0.2f}, 3, ownerId);

        assertThat(requestPath.get()).isEqualTo("/collections/materials/points/query");
        assertThat(requestBody.get().path("query")).hasSize(2);
        assertThat(requestBody.get().has("vector")).isFalse();
        assertThat(requestBody.get().path("limit").asInt()).isEqualTo(3);
        assertThat(requestBody.get().path("with_payload").asBoolean()).isTrue();
        assertThat(requestBody.get().path("filter").path("must").get(0).path("match").path("value").asText())
                .isEqualTo("approved");
        assertThat(requestBody.get().path("filter").path("should"))
                .anySatisfy(condition -> assertThat(condition.path("match").path("value").asText())
                        .isEqualTo(ownerId.toString()));
        assertThat(matches).containsExactly(new VectorMatch("chunk-1", 0.91, materialId, "Title", "Text", 2));
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
