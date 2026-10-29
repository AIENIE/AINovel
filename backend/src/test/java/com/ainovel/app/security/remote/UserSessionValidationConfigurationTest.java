package com.ainovel.app.security.remote;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class UserSessionValidationConfigurationTest {
    static Stream<Arguments> timeoutConfigurations() {
        var cases = java.util.List.of(
                Map.entry(Map.<String,String>of(), 5000L),
                Map.entry(Map.of("app.external.session-timeout-ms", "3000"), 3000L),
                Map.entry(Map.of("EXTERNAL_SESSION_TIMEOUT_MS", "3400"), 3400L),
                Map.entry(Map.of("runtime.configuration.EXTERNAL_SESSION_TIMEOUT_MS", "3500"), 3500L),
                Map.entry(Map.of("USER_SESSION_GRPC_TIMEOUT_MS", "6000", "app.external.session-timeout-ms", "3000"), 6000L),
                Map.entry(Map.of("runtime.configuration.USER_SESSION_GRPC_TIMEOUT_MS", "6100", "EXTERNAL_SESSION_TIMEOUT_MS", "3400"), 6100L),
                Map.entry(Map.of("sso.session-validation.timeout-ms", "5000", "app.external.session-timeout-ms", "3000"), 5000L),
                Map.entry(Map.of("sso.session-validation.timeout-ms", "5500", "USER_SESSION_GRPC_TIMEOUT_MS", "6000"), 5500L),
                Map.entry(Map.of("app.external.session-timeout-ms", "3100", "EXTERNAL_SESSION_TIMEOUT_MS", "3400"), 3100L),
                Map.entry(Map.of("runtime.configuration.USER_SESSION_GRPC_TIMEOUT_MS", "6100", "USER_SESSION_GRPC_TIMEOUT_MS", "6000"), 6100L));
        return Stream.of(false, true).flatMap(local -> cases.stream().map(test -> Arguments.of(local, test.getKey(), test.getValue())));
    }

    @ParameterizedTest(name="local={0}, configuration={1}, expected={2}")
    @MethodSource("timeoutConfigurations")
    void resolvesOneSessionDeadlineBeforeBinding(boolean local, Map<String,String> configured, long expected) throws IOException {
        var environment = new MockEnvironment();
        var loader = new YamlPropertySourceLoader();
        // The profile is higher priority than the base; explicit test properties precede both.
        for (String name : local ? new String[]{"application-local.yml", "application.yml"} : new String[]{"application.yml"})
            for (var source : loader.load(name, new FileSystemResource("src/main/resources/" + name)))
                environment.getPropertySources().addLast(source);
        configured.forEach(environment::setProperty);
        var properties = Binder.get(environment).bind("sso.session-validation", Bindable.of(UserSessionValidationProperties.class)).get();
        assertEquals(expected, properties.getTimeoutMs());
        if (configured.isEmpty()) {
            assertNull(environment.getProperty("app.external.session-timeout-ms"));
            assertEquals(5000, new UserSessionValidationProperties().getTimeoutMs());
        }
    }
}
