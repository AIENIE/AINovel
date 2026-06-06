package com.ainovel.app.metrics;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ApiRequestMetrics {
    private final AtomicLong total = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void record(int status) {
        total.incrementAndGet();
        if (status >= 500) {
            errors.incrementAndGet();
        }
    }

    public double errorRate() {
        long seen = total.get();
        if (seen == 0) {
            return 0.0;
        }
        return (double) errors.get() / seen;
    }
}
