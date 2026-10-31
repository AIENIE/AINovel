package com.ainovel.app.security.remote;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class UserServiceHealthIndicator implements HealthIndicator {
    private final UserSessionValidator validator;

    public UserServiceHealthIndicator(UserSessionValidator validator) { this.validator = validator; }

    @Override
    public Health health() {
        return validator.dependencyAvailable()
                ? Health.up().withDetail("discovery", "passing-endpoint").build()
                : Health.down().withDetail("discovery", "no-passing-endpoint").build();
    }
}
