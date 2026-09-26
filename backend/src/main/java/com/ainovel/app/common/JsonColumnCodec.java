package com.ainovel.app.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class JsonColumnCodec {
    private final ObjectMapper objectMapper;

    public JsonColumnCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Required stored data must never be converted into an apparently valid empty value. */
    public <T> T readRequired(String json, TypeReference<T> type) {
        try {
            if (json == null || json.isBlank()) throw new IllegalArgumentException();
            T value = objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
            if (value == null) throw new IllegalArgumentException();
            return value;
        } catch (Exception ex) {
            // Do not attach parser exceptions: they may contain manuscript text.
            throw new ApiStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_DATA_INVALID");
        }
    }

    /** JSON coercion (numbers/null to strings) is not valid for a manuscript section. */
    public Map<String, String> readSections(String json) {
        JsonNode root = readRequired(json, new TypeReference<JsonNode>() { });
        if (!root.isObject()) throw invalidStoredData();
        Map<String, String> result = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) throw invalidStoredData();
            result.put(entry.getKey(), entry.getValue().textValue());
        });
        return result;
    }

    public String writeRequired(Object value) {
        try {
            if (value == null) throw new IllegalArgumentException();
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ApiStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_DATA_SERIALIZATION_FAILED");
        }
    }

    private ApiStatusException invalidStoredData() {
        return new ApiStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "STORED_DATA_INVALID");
    }

    public <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public String write(Object value, String fallback) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return fallback;
        }
    }
}
