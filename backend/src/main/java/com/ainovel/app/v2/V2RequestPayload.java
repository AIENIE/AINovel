package com.ainovel.app.v2;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Compatibility envelope for extensible v2 JSON bodies. Controllers expose a
 * concrete record instead of an unchecked Map; persistence code receives a
 * defensive copy only after Jackson has verified that the body is an object.
 */
public record V2RequestPayload(JsonNode value) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public V2RequestPayload {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("request body must be a JSON object");
        }
    }

    public Object get(String field) {
        JsonNode node = value.get(field);
        return node == null || node.isNull() ? null : MAPPER.convertValue(node, Object.class);
    }

    public Object getOrDefault(String field, Object fallback) {
        Object result = get(field);
        return result == null ? fallback : result;
    }

    public Map<String, Object> asMap() {
        return MAPPER.convertValue(value, MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
    }

    public static V2RequestPayload of(Map<String, ?> values) {
        return new V2RequestPayload(MAPPER.valueToTree(values));
    }
}
