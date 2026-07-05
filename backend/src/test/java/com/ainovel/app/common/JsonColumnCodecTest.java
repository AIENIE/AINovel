package com.ainovel.app.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonColumnCodecTest {

    @Test
    void readShouldReturnFallbackForBlankOrInvalidJson() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonColumnCodec codec = new JsonColumnCodec(objectMapper);
        Map<String, Object> fallback = Map.of("fallback", true);

        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new RuntimeException("bad json"));

        assertSame(fallback, codec.read(null, new TypeReference<Map<String, Object>>() {}, fallback));
        assertSame(fallback, codec.read("   ", new TypeReference<Map<String, Object>>() {}, fallback));
        assertSame(fallback, codec.read("{bad}", new TypeReference<Map<String, Object>>() {}, fallback));
    }

    @Test
    void readShouldParseTypedCollections() {
        JsonColumnCodec codec = new JsonColumnCodec(new ObjectMapper());

        Map<String, String> sections = codec.read(
                "{\"scene-1\":\"hello\"}",
                new TypeReference<Map<String, String>>() {},
                Map.of()
        );
        List<String> tags = codec.read(
                "[\"mystery\",\"harbor\"]",
                new TypeReference<List<String>>() {},
                List.of()
        );

        assertEquals(Map.of("scene-1", "hello"), sections);
        assertEquals(List.of("mystery", "harbor"), tags);
    }

    @Test
    void writeShouldReturnFallbackOnSerializationFailure() throws Exception {
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonColumnCodec codec = new JsonColumnCodec(objectMapper);

        when(objectMapper.writeValueAsString(any())).thenThrow(new RuntimeException("cannot serialize"));

        assertEquals("[]", codec.write(List.of("x"), "[]"));
    }

    @Test
    void writeShouldSerializeValue() {
        JsonColumnCodec codec = new JsonColumnCodec(new ObjectMapper());

        assertEquals("{\"scene-1\":\"hello\"}", codec.write(Map.of("scene-1", "hello"), "{}"));
    }
}
