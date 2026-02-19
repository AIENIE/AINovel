package com.ainovel.app.auth;

import com.ainovel.app.integration.ConsulServiceResolver;
import com.ainovel.app.integration.ExternalServiceProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SsoEntryServiceTest {

    @Test
    void shouldBuildLoginRedirectUriFromConsulEndpoint() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setServiceName("aienie-userservice-http");
        properties.getUserserviceHttp().setFallback("http://fallback:10002");
        ConsulServiceResolver resolver = mock(ConsulServiceResolver.class);
        when(resolver.resolveOrFallback("aienie-userservice-http", "http://fallback:10002"))
                .thenReturn(Optional.of(new ConsulServiceResolver.Endpoint("127.0.0.1", 10002)));

        SsoEntryService service = new SsoEntryService(properties, resolver);
        URI uri = service.buildLoginRedirectUri(
                "https://ainovel.aienie.com/sso/callback?next=/workbench",
                "state-abc"
        );

        assertEquals("http", uri.getScheme());
        assertEquals("127.0.0.1", uri.getHost());
        assertEquals(10002, uri.getPort());
        assertEquals("/sso/login", uri.getPath());
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("redirect=https://ainovel.aienie.com/sso/callback?next=/workbench"));
        assertTrue(query.contains("state=state-abc"));
    }

    @Test
    void shouldRejectInvalidFallback() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setServiceName("aienie-userservice-http");
        properties.getUserserviceHttp().setFallback("invalid");
        ConsulServiceResolver resolver = mock(ConsulServiceResolver.class);
        when(resolver.resolveOrFallback("aienie-userservice-http", "invalid"))
                .thenReturn(Optional.empty());

        SsoEntryService service = new SsoEntryService(properties, resolver);
        assertThrows(IllegalStateException.class,
                () -> service.buildRegisterRedirectUri("http://ainovel.seekerhut.com/sso/callback?next=/workbench", "state"));
    }
}
