package com.ainovel.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ainovel.app.integration.ExternalServiceProperties;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SsoTokenExchangeServiceTest {

    @Test
    void localTlsFallbackAcceptsTesthutButNotExternalHosts() throws Exception {
        SsoTokenExchangeService service = new SsoTokenExchangeService(
                new SsoEntryService(new ExternalServiceProperties()),
                new ObjectMapper()
        );
        Method method = SsoTokenExchangeService.class.getDeclaredMethod("allowsLocalInsecureTls", URI.class);
        method.setAccessible(true);

        assertTrue((Boolean) method.invoke(service, URI.create("https://localuserservice.testhut.top/sso/token")));
        assertTrue((Boolean) method.invoke(service, URI.create("https://userservice.localhut.com/sso/token")));
        assertFalse((Boolean) method.invoke(service, URI.create("https://userservice.example.com/sso/token")));
        assertFalse((Boolean) method.invoke(service, URI.create("http://localuserservice.testhut.top/sso/token")));
    }
}
