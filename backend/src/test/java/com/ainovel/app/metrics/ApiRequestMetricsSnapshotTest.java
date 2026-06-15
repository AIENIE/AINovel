package com.ainovel.app.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiRequestMetricsSnapshotTest {

    @Test
    void snapshotIncludesStatusDistributionLatencyAndRecentErrors() {
        ApiRequestMetrics metrics = new ApiRequestMetrics();

        metrics.record(200, 12);
        metrics.record(201, 18);
        metrics.record(500, 75);
        metrics.record(503, 120);

        ApiRequestMetrics.Snapshot snapshot = metrics.snapshot();

        assertEquals(4L, snapshot.totalRequests());
        assertEquals(2L, snapshot.errorRequests());
        assertEquals(0.5, snapshot.errorRate());
        assertEquals(1L, snapshot.statusCounts().get(200));
        assertEquals(1L, snapshot.statusCounts().get(503));
        assertTrue(snapshot.p95LatencyMs() >= 75L);
        assertTrue(snapshot.p99LatencyMs() >= snapshot.p95LatencyMs());
        assertEquals(2, snapshot.recentErrors().size());
        assertEquals(503, snapshot.recentErrors().getLast().status());
    }
}
