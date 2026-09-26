package com.ainovel.app.material;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.junit.jupiter.api.Assertions.*;

class MaterialIndexCapacityConfigTest {
    @Test void workerAndQueueBoundsAreAppliedFromConfiguration() {
        var configuration = new MaterialIndexAsyncConfig();
        var executor = (ThreadPoolTaskExecutor) configuration.materialIndexExecutor(2, 16);
        try {
            assertEquals(2, executor.getMaxPoolSize());
            assertEquals(16, executor.getThreadPoolExecutor().getQueue().remainingCapacity());
        } finally { executor.shutdown(); }
        assertThrows(IllegalArgumentException.class, () -> configuration.materialIndexExecutor(0, 16));
    }
}
