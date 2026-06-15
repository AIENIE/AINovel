package com.ainovel.app.metrics;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class ApiRequestMetrics {
    private static final int MAX_SAMPLES = 2_000;
    private static final int MAX_RECENT_ERRORS = 20;

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();
    private final ConcurrentLinkedDeque<RequestSample> samples = new ConcurrentLinkedDeque<>();
    private final ConcurrentLinkedDeque<RecentError> recentErrors = new ConcurrentLinkedDeque<>();

    public void record(int status) {
        record(status, 0L);
    }

    public void record(int status, long latencyMs) {
        total.incrementAndGet();
        if (status >= 500) {
            errors.incrementAndGet();
            recentErrors.addLast(new RecentError(status, Math.max(0L, latencyMs), Instant.now()));
            trim(recentErrors, MAX_RECENT_ERRORS);
        }
        samples.addLast(new RequestSample(status, Math.max(0L, latencyMs), Instant.now()));
        trim(samples, MAX_SAMPLES);
    }

    public double errorRate() {
        long seen = total.get();
        if (seen == 0) {
            return 0.0;
        }
        return (double) errors.get() / seen;
    }

    public Snapshot snapshot() {
        List<RequestSample> copy = new ArrayList<>(samples);
        Map<Integer, Long> statusCounts = copy.stream()
                .collect(Collectors.groupingBy(RequestSample::status, LinkedHashMap::new, Collectors.counting()));
        List<Long> latencies = copy.stream()
                .map(RequestSample::latencyMs)
                .sorted(Comparator.naturalOrder())
                .toList();
        return new Snapshot(
                total.get(),
                errors.get(),
                errorRate(),
                statusCounts,
                percentile(latencies, 0.95),
                percentile(latencies, 0.99),
                new ArrayList<>(recentErrors)
        );
    }

    private long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(sorted.size() * percentile) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private <T> void trim(ConcurrentLinkedDeque<T> deque, int maxSize) {
        while (deque.size() > maxSize) {
            deque.pollFirst();
        }
    }

    private record RequestSample(int status, long latencyMs, Instant createdAt) {
    }

    public record RecentError(int status, long latencyMs, Instant createdAt) {
    }

    public record Snapshot(
            long totalRequests,
            long errorRequests,
            double errorRate,
            Map<Integer, Long> statusCounts,
            long p95LatencyMs,
            long p99LatencyMs,
            List<RecentError> recentErrors
    ) {
    }
}
