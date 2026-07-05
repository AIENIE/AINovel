package com.ainovel.app.admin.ops;

import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.security.remote.UserSessionValidationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
}
