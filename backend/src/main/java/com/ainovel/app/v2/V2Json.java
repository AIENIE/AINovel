package com.ainovel.app.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ainovel.app.common.JsonColumnCodec;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
final class V2Json {
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final JsonColumnCodec codec;

    V2Json(ObjectMapper objectMapper) {
        this.codec = new JsonColumnCodec(objectMapper);
    }

    String write(Object value) {
        return codec.writeRequired(value == null ? Map.of() : value);
    }

    List<Object> list(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        return codec.readRequired(json, LIST_TYPE);
    }

    Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return codec.readRequired(json, MAP_TYPE);
    }
}
