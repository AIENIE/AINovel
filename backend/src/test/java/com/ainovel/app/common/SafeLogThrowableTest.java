package com.ainovel.app.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class SafeLogThrowableTest {

    @Test
    void keepsTypeAndStackWithoutMessageCauseOrSuppressed() {
        IllegalArgumentException cause = new IllegalArgumentException("cause-secret");
        IllegalStateException source = new IllegalStateException("Bearer response-secret", cause);
        source.addSuppressed(new RuntimeException("suppressed-secret"));

        SafeLogThrowable safe = SafeLogThrowable.stackOnly(source);

        assertEquals(IllegalStateException.class.getName(), safe.originalType());
        assertNull(safe.getMessage());
        assertArrayEquals(source.getStackTrace(), safe.getStackTrace());
        assertNull(safe.getCause());
        assertEquals(0, safe.getSuppressed().length);
        assertFalse(safe.toString().contains("response-secret"));
        assertFalse(safe.toString().contains("cause-secret"));
        assertFalse(safe.toString().contains("suppressed-secret"));
    }
}
