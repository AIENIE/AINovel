package com.ainovel.app.auth;

import com.ainovel.app.integration.ExternalServiceProperties;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoEntryServiceTest {

    @Test
    void shouldUseConfiguredHttpsAddressForLoginRedirect() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setAddress("https://userservice.seekerhut.com");

        SsoEntryService service = new SsoEntryService(properties);
        URI uri = service.buildLoginRedirectUri(
                "https://ainovel.aienie.com/sso/callback?next=/workbench",
                "state-abc"
        );

        assertEquals("https", uri.getScheme());
        assertEquals("userservice.seekerhut.com", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertEquals("/sso/login", uri.getPath());
        String query = URLDecoder.decode(uri.getRawQuery(), StandardCharsets.UTF_8);
        assertTrue(query.contains("redirect=https://ainovel.aienie.com/sso/callback?next=/workbench"));
        assertTrue(query.contains("state=state-abc"));
    }

    @Test
    void shouldConvertHostPortAddressIntoHttpRedirectBase() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setAddress("userservice.localhut.com:10000");

        SsoEntryService service = new SsoEntryService(properties);
        URI uri = service.buildRegisterRedirectUri("https://ainovel.localhut.com/sso/callback", "state-xyz");

        assertEquals("http", uri.getScheme());
        assertEquals("userservice.localhut.com", uri.getHost());
        assertEquals(10000, uri.getPort());
        assertEquals("/register", uri.getPath());
    }

    @Test
    void shouldRejectInvalidAddress() {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setAddress("invalid");

        SsoEntryService service = new SsoEntryService(properties);
        assertThrows(IllegalStateException.class,
                () -> service.buildRegisterRedirectUri("http://ainovel.seekerhut.com/sso/callback?next=/workbench", "state"));
    }
}
