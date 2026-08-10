package com.ainovel.app.admin.ops;

import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.security.remote.UserSessionValidationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DependencyHealthServiceTest {

    @Test
    void redisProbeShouldUseConfiguredConnectionFactory() {
        RedisConnectionFactory redisConnectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection redisConnection = mock(RedisConnection.class);
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);

        DependencyHealthService service = new DependencyHealthService(
                mock(JdbcTemplate.class),
                redisConnectionFactory,
                mock(ExternalServiceProperties.class),
                mock(UserSessionValidationProperties.class),
                mock(OpsRecordFileSink.class)
        );

        ReflectionTestUtils.invokeMethod(service, "checkRedis");

        verify(redisConnectionFactory).getConnection();
        verify(redisConnection).ping();
        verify(redisConnection).close();
    }

    @Test
    void dependencyMessageShouldBeBoundedAndRedactCommonSecretForms() {
        DependencyHealthService service = service(mock(RedisConnectionFactory.class));
        String raw = "Authorization: Bearer auth-value password=hunter2 secret: 'secret-value' "
                + "api_key=query-key Bearer standalone-token "
                + "https://url-user:url-password@example.test/path?token=query-token&key=query-secret "
                + "x".repeat(1_000);

        String cleaned = ReflectionTestUtils.invokeMethod(service, "clean", raw);

        assertNotNull(cleaned);
        assertTrue(cleaned.length() <= 512);
        assertTrue(cleaned.contains("<redacted>"));
        assertFalse(cleaned.contains("auth-value"));
        assertFalse(cleaned.contains("hunter2"));
        assertFalse(cleaned.contains("secret-value"));
        assertFalse(cleaned.contains("query-key"));
        assertFalse(cleaned.contains("standalone-token"));
        assertFalse(cleaned.contains("url-user"));
        assertFalse(cleaned.contains("url-password"));
        assertFalse(cleaned.contains("query-token"));
        assertFalse(cleaned.contains("query-secret"));
    }

    private DependencyHealthService service(RedisConnectionFactory redisConnectionFactory) {
        return new DependencyHealthService(
                mock(JdbcTemplate.class),
                redisConnectionFactory,
                mock(ExternalServiceProperties.class),
                mock(UserSessionValidationProperties.class),
                mock(OpsRecordFileSink.class)
        );
    }
}
