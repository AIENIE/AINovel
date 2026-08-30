package com.ainovel.app.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class V2RequestPayloadTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsObjectsAndCreatesDefensiveMap() throws Exception {
        V2RequestPayload payload = mapper.readValue("{\"name\":\"draft\",\"count\":2}", V2RequestPayload.class);
        assertEquals("draft", payload.get("name"));
        assertEquals(2, payload.get("count"));
        assertEquals("draft", payload.asMap().get("name"));
    }

    @Test
    void rejectsNonObjectBodies() {
        assertThrows(Exception.class, () -> mapper.readValue("[]", V2RequestPayload.class));
    }
}
