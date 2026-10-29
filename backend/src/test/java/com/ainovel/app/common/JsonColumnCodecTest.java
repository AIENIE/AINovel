package com.ainovel.app.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JsonColumnCodecTest {

    @Test
    void criticalSectionsRejectCorruptionNullAndCoercedValues() {
        JsonColumnCodec codec = new JsonColumnCodec(new ObjectMapper());
        for (String invalid : new String[]{null, "", "null", "{bad}", "[]", "{} {}", "{\"scene\":null}", "{\"scene\":42}"}) {
            ApiStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ApiStatusException.class, () -> codec.readSections(invalid));
            assertEquals("STORED_DATA_INVALID", error.getMessage());
            org.junit.jupiter.api.Assertions.assertNull(error.getCause());
        }
        assertEquals(Map.of("scene", "正文"), codec.readSections("{\"scene\":\"正文\"}"));
        assertEquals(Map.of(), codec.readSections("{}"));
    }

    @Test
    void criticalWritesFailWithoutParserDetailsOrEmptyFallback() throws Exception {
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new RuntimeException("private manuscript text"));
        ApiStatusException error = org.junit.jupiter.api.Assertions.assertThrows(ApiStatusException.class,
                () -> new JsonColumnCodec(mapper).writeRequired(Map.of("scene", "text")));
        assertEquals("STORED_DATA_SERIALIZATION_FAILED", error.getMessage());
        org.junit.jupiter.api.Assertions.assertNull(error.getCause());
    }

    @Test
    void optionalBlankValuesRemainDistinctFromCorruptStoredData() {
        JsonColumnCodec codec = new JsonColumnCodec(new ObjectMapper());
        Map<String, Object> fallback = Map.of("fallback", true);
        var type = new TypeReference<Map<String, Object>>() {};
        assertSame(fallback, codec.read(null, type, fallback));
        assertSame(fallback, codec.read("   ", type, fallback));
        for (String invalid : new String[]{"{bad}", "null", "[]", "{} {}"}) {
            org.junit.jupiter.api.Assertions.assertThrows(ApiStatusException.class,
                    () -> codec.read(invalid, type, fallback));
        }
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
    void writeShouldSerializeValue() {
        JsonColumnCodec codec = new JsonColumnCodec(new ObjectMapper());

        assertEquals("{\"scene-1\":\"hello\"}", codec.writeRequired(Map.of("scene-1", "hello")));
    }
}
