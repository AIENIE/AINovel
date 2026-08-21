package com.ainovel.app.integration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StagingTrustPolicyTest {
    @Test
    void acceptsOnlyTheFixedRootInStaging() {
        assertDoesNotThrow(() -> new StagingTrustPolicy(
                "test", StagingTrustPolicy.STAGING_ROOT, true, false).validate());
        assertThrows(IllegalStateException.class,
                () -> new StagingTrustPolicy("test", "/tmp/other.pem", true, false).validate());
        assertThrows(IllegalStateException.class,
                () -> new StagingTrustPolicy(
                        "test", StagingTrustPolicy.STAGING_ROOT, false, true).validate());
    }

    @Test
    void productionRequiresPublicSystemTrust() {
        assertDoesNotThrow(() -> new StagingTrustPolicy("production", "", true, false).validate());
        assertThrows(IllegalStateException.class,
                () -> new StagingTrustPolicy(
                        "production", StagingTrustPolicy.STAGING_ROOT, true, false).validate());
    }
}
