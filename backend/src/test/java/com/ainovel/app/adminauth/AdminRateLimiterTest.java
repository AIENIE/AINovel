package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdminRateLimiterTest {
    @Test
    void productionFailsClosedWhenTheSharedStoreIsUnavailable() {
        StringRedisTemplate redis = unavailableRedis();
        AdminRateLimiter limiter = new AdminRateLimiter(redis, policy("production", "totp"));

        assertFalse(limiter.allow("login:account:admin", 5, Duration.ofMinutes(1)));
    }

    @Test
    void localModeUsesABoundedProcessLocalFallback() {
        StringRedisTemplate redis = unavailableRedis();
        AdminRateLimiter limiter = new AdminRateLimiter(redis, policy("local", "password"));

        assertTrue(limiter.allow("login:account:admin", 2, Duration.ofMinutes(1)));
        assertTrue(limiter.allow("login:account:admin", 2, Duration.ofMinutes(1)));
        assertFalse(limiter.allow("login:account:admin", 2, Duration.ofMinutes(1)));
    }

    private StringRedisTemplate unavailableRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        return redis;
    }

    private AdminAuthPolicySource policy(String env, String mode) {
        return new AdminAuthPolicySource() {
            @Override public String env() { return env; }
            @Override public String authMode() { return mode; }
        };
    }
}
