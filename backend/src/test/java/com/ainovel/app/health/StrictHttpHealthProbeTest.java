package com.ainovel.app.health;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrictHttpHealthProbeTest {
    @Test
    void acceptsOnlyTheUniqueTopLevelUpStatus() {
        assertTrue(StrictHttpHealthProbe.isStrictUpBody(
                "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8)));
        assertTrue(StrictHttpHealthProbe.isStrictUpBody(
                "{\"status\":\"UP\",\"groups\":[\"liveness\",\"readiness\"]}"
                        .getBytes(StandardCharsets.UTF_8)));
        assertFalse(StrictHttpHealthProbe.isStrictUpBody(
                "{\"status\":\"UP\",\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8)));
        assertFalse(StrictHttpHealthProbe.isStrictUpBody(new byte[]{(byte) 0xc3, (byte) 0x28}));
    }
}
