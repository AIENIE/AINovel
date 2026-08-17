package com.ainovel.app.ai;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.common.ApiStatusException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class AiAdmissionGuardTest {
    private final AiAdmissionGuard guard = new AiAdmissionGuard(
            mock(StringRedisTemplate.class), new ObjectMapper(), 2, 8, 12, 10, 1, 2);

    @Test
    void validatesSupportedRolesAndRequiresUserMessage() {
        assertDoesNotThrow(() -> guard.validate(request(
                new AiChatRequest.Message("system", "rules"),
                new AiChatRequest.Message("user", "hello"))));

        ApiStatusException missingUser = assertThrows(ApiStatusException.class,
                () -> guard.validate(request(new AiChatRequest.Message("assistant", "hello"))));
        assertEquals("AI_REQUEST_TOO_LARGE", missingUser.getMessage());

        ApiStatusException badRole = assertThrows(ApiStatusException.class,
                () -> guard.validate(request(new AiChatRequest.Message("tool", "hello"))));
        assertEquals("AI_MESSAGE_ROLE_INVALID", badRole.getMessage());
    }

    @Test
    void rejectsMessageAndAggregateLimits() {
        assertEquals("AI_MESSAGE_CONTENT_INVALID", assertThrows(ApiStatusException.class,
                () -> guard.validate(request(new AiChatRequest.Message("user", "123456789")))).getMessage());
        assertEquals("AI_MESSAGES_INVALID", assertThrows(ApiStatusException.class,
                () -> guard.validate(request(
                        new AiChatRequest.Message("user", "one"),
                        new AiChatRequest.Message("assistant", "two"),
                        new AiChatRequest.Message("user", "three")))).getMessage());
        assertEquals("AI_REQUEST_TOO_LARGE", assertThrows(ApiStatusException.class,
                () -> guard.validate(request(
                        new AiChatRequest.Message("user", "12345678"),
                        new AiChatRequest.Message("assistant", "12345678")))).getMessage());
    }

    private AiChatRequest request(AiChatRequest.Message... messages) {
        return new AiChatRequest(List.of(messages), "gpt", null);
    }
}
