package com.ainovel.app.adminauth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class AdminRateLimiter {
    private static final int MAX_LOCAL_WINDOWS = 10_000;
    private final StringRedisTemplate redis;
    private final AdminAuthPolicySource policy;
    private final ConcurrentMap<String, LocalWindow> localWindows = new ConcurrentHashMap<>();

    public AdminRateLimiter(StringRedisTemplate redis, AdminAuthPolicySource policy) {
        this.redis = redis;
        this.policy = policy;
    }

    public boolean allow(String key, int limit, Duration window) {
        try {
            String redisKey = "ainovel:admin-auth:rate:" + key;
            Long count = redis.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                redis.expire(redisKey, window);
            }
            return count != null && count <= limit;
        } catch (RuntimeException ex) {
            if (AdminAuthPolicySource.ENV_PRODUCTION.equals(policy.env())) {
                return false;
            }
            return allowLocally(key, limit, window);
        }
    }

    private boolean allowLocally(String key, int limit, Duration window) {
        long now = System.nanoTime();
        if (!localWindows.containsKey(key) && localWindows.size() >= MAX_LOCAL_WINDOWS) {
            localWindows.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() <= now);
            if (localWindows.size() >= MAX_LOCAL_WINDOWS) {
                return false;
            }
        }
        long expiresAt = now + Math.max(1, window.toNanos());
        LocalWindow result = localWindows.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAtNanos() <= now) {
                return new LocalWindow(1, expiresAt);
            }
            return new LocalWindow(current.count() + 1, current.expiresAtNanos());
        });
        return result.count() <= limit;
    }

    private record LocalWindow(int count, long expiresAtNanos) {
    }
}
