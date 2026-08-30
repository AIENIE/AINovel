package com.ainovel.app.security;

import com.ainovel.app.settings.repo.GlobalSettingsRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class MaintenanceModeCache {
    private static final Duration TTL = Duration.ofSeconds(2);
    private final GlobalSettingsRepository repository;
    private volatile boolean value;
    private volatile Instant expiresAt = Instant.EPOCH;

    public MaintenanceModeCache(GlobalSettingsRepository repository) { this.repository = repository; }

    public boolean enabled() {
        Instant now = Instant.now();
        if (expiresAt.isAfter(now)) return value;
        synchronized (this) {
            if (expiresAt.isAfter(now)) return value;
            value = repository.findTopByOrderByUpdatedAtDesc().map(item -> item.isMaintenanceMode()).orElse(false);
            expiresAt = now.plus(TTL);
            return value;
        }
    }

    public void invalidate() { expiresAt = Instant.EPOCH; }
}
