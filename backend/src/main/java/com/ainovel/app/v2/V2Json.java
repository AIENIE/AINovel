package com.ainovel.app.v2;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
final class V2Json {
    private static final TypeReference<List<Object>> LIST_TYPE = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private final ObjectMapper objectMapper;

    V2Json(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    List<Object> list(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, LIST_TYPE);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    Map<String, Object> map(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }
}
