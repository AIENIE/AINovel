package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class AiAdmissionGuard {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>("""
            local rate = redis.call('INCR', KEYS[1])
            if rate == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if rate > tonumber(ARGV[2]) then return -1 end
            local user = tonumber(redis.call('GET', KEYS[2]) or '0')
            if user >= tonumber(ARGV[3]) then return -2 end
            local global = tonumber(redis.call('GET', KEYS[3]) or '0')
            if global >= tonumber(ARGV[4]) then return -3 end
            redis.call('INCR', KEYS[2]); redis.call('PEXPIRE', KEYS[2], ARGV[5])
            redis.call('INCR', KEYS[3]); redis.call('PEXPIRE', KEYS[3], ARGV[5])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            for i = 1, 2 do
              local value = tonumber(redis.call('GET', KEYS[i]) or '0')
              if value <= 1 then redis.call('DEL', KEYS[i]) else redis.call('DECR', KEYS[i]) end
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final int maxMessages;
    private final int maxMessageChars;
    private final int maxTotalChars;
    private final int maxRequestsPerMinute;
    private final int maxUserInFlight;
    private final int maxGlobalInFlight;

    public AiAdmissionGuard(StringRedisTemplate redis, ObjectMapper objectMapper,
                            @Value("${app.ai.admission.max-messages:64}") int maxMessages,
                            @Value("${app.ai.admission.max-message-chars:20000}") int maxMessageChars,
                            @Value("${app.ai.admission.max-total-chars:100000}") int maxTotalChars,
                            @Value("${app.ai.admission.max-requests-per-minute:20}") int maxRequestsPerMinute,
                            @Value("${app.ai.admission.max-user-in-flight:2}") int maxUserInFlight,
                            @Value("${app.ai.admission.max-global-in-flight:32}") int maxGlobalInFlight) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.maxMessages = maxMessages;
        this.maxMessageChars = maxMessageChars;
        this.maxTotalChars = maxTotalChars;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
        this.maxUserInFlight = maxUserInFlight;
        this.maxGlobalInFlight = maxGlobalInFlight;
    }

    public Lease acquire(User user, AiChatRequest request) {
        validate(request);
        String userId = user.getId().toString();
        String rateKey = "{ai-admission}:rate:" + userId;
        String userKey = "{ai-admission}:flight:user:" + userId;
        String globalKey = "{ai-admission}:flight:global";
        final Long outcome;
        try {
            outcome = redis.execute(ACQUIRE, List.of(rateKey, userKey, globalKey),
                    "60000", String.valueOf(maxRequestsPerMinute), String.valueOf(maxUserInFlight),
                    String.valueOf(maxGlobalInFlight), "120000");
        } catch (RuntimeException ex) {
            throw new ApiStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI_ADMISSION_UNAVAILABLE");
        }
        if (outcome == null || outcome < 1) {
            String code = outcome != null && outcome == -1 ? "AI_RATE_LIMITED"
                    : outcome != null && outcome == -2 ? "AI_USER_CONCURRENCY_LIMIT"
                    : "AI_GLOBAL_CONCURRENCY_LIMIT";
            throw new ApiStatusException(HttpStatus.TOO_MANY_REQUESTS, code);
        }
        return new Lease(redis, userKey, globalKey);
    }

    public String requestHash(AiChatRequest request) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to hash AI request", ex);
        }
    }

    public void validate(AiChatRequest request) {
        if (request == null || request.messages() == null || request.messages().isEmpty()
                || request.messages().size() > maxMessages) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_MESSAGES_INVALID");
        }
        int total = 0;
        boolean hasUser = false;
        for (AiChatRequest.Message message : request.messages()) {
            if (message == null) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_MESSAGE_INVALID");
            }
            String role = message.role() == null ? "" : message.role().trim().toLowerCase(Locale.ROOT);
            if (!List.of("system", "user", "assistant").contains(role)) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_MESSAGE_ROLE_INVALID");
            }
            String content = message.content() == null ? "" : message.content();
            if (content.isBlank() || content.length() > maxMessageChars) {
                throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_MESSAGE_CONTENT_INVALID");
            }
            total += content.length();
            hasUser |= "user".equals(role);
        }
        if (!hasUser || total > maxTotalChars || serializedContextBytes(request) > maxTotalChars) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_REQUEST_TOO_LARGE");
        }
    }

    private int serializedContextBytes(AiChatRequest request) {
        if (request.context() == null) return 0;
        try {
            return objectMapper.writeValueAsString(request.context()).getBytes(StandardCharsets.UTF_8).length;
        } catch (JsonProcessingException ex) {
            throw new ApiStatusException(HttpStatus.BAD_REQUEST, "AI_CONTEXT_INVALID");
        }
    }

    public static final class Lease implements AutoCloseable {
        private final StringRedisTemplate redis;
        private final String userKey;
        private final String globalKey;
        private boolean closed;

        private Lease(StringRedisTemplate redis, String userKey, String globalKey) {
            this.redis = redis;
            this.userKey = userKey;
            this.globalKey = globalKey;
        }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try { redis.execute(RELEASE, List.of(userKey, globalKey)); } catch (RuntimeException ignored) { }
        }
    }
}
