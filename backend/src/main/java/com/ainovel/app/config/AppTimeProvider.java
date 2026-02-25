package com.ainovel.app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class AppTimeProvider {
    private final ZoneId zoneId;
    private final Clock clock;

    public AppTimeProvider(@Value("${app.time-zone:Asia/Shanghai}") String zoneIdText) {
        this.zoneId = ZoneId.of(zoneIdText);
        this.clock = Clock.system(this.zoneId);
    }

    public ZoneId zoneId() {
        return zoneId;
    }

    public Clock clock() {
        return clock;
    }

    public Instant nowInstant() {
        return Instant.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public LocalDate toLocalDate(Instant instant) {
        return instant.atZone(zoneId).toLocalDate();
    }
}
