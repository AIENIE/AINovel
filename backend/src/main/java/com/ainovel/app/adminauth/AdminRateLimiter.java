package com.ainovel.app.adminauth;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class AdminRateLimiter {
    private final StringRedisTemplate redis;

    public AdminRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
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
            return false;
        }
    }
}
