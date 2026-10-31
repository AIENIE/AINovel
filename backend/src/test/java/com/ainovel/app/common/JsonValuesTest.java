package com.ainovel.app.common;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JsonValuesTest {
    @Test void omittedValuesKeepTheAuthorDefaultAndValidLegacyValuesRemainReadable() {
        assertEquals(7, JsonValues.integer(null, 7));
        assertEquals(123, JsonValues.integer("123", 7));
        assertEquals(3, JsonValues.integer(3L, 7));
        assertTrue(JsonValues.bool(null, true));
        assertFalse(JsonValues.bool("false", true));
    }
    @Test void malformedPresentValuesCannotBecomeDefaultValues() {
        for (Object value : List.of("invalid", "", 1.5d, Long.MAX_VALUE, true))
            assertThrows(BusinessException.class, () -> JsonValues.integer(value, 7));
        assertThrows(BusinessException.class, () -> JsonValues.bool("invalid", false));
        assertThrows(BusinessException.class, () -> JsonValues.bool(0, false));
    }
}
