package com.ainovel.app.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoControllerTest {

    @Test
    void shouldRedirectToLoginAndSanitizeNextPath() {
        SsoEntryService entryService = mock(SsoEntryService.class);
        SsoController controller = new SsoController(entryService);
        URI target = URI.create("http://127.0.0.1:10002/sso/login?redirect=r&state=s");
        when(entryService.buildLoginRedirectUri(anyString(), eq("state-1"))).thenReturn(target);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sso/login");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "ainovel.aienie.com");

        ResponseEntity<Void> response = controller.login("https://malicious.example/path", "state-1", request);
        assertEquals(HttpStatus.FOUND, response.getStatusCode());
        assertEquals(target, response.getHeaders().getLocation());

        ArgumentCaptor<String> callbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(entryService).buildLoginRedirectUri(callbackCaptor.capture(), eq("state-1"));
        String callback = callbackCaptor.getValue();
        assertTrue(callback.startsWith("https://ainovel.aienie.com/sso/callback"));
        String next = UriComponentsBuilder.fromUriString(callback).build().getQueryParams().getFirst("next");
        assertEquals("/workbench", next);
    }

    @Test
    void shouldRequireState() {
        SsoEntryService entryService = mock(SsoEntryService.class);
        SsoController controller = new SsoController(entryService);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sso/login");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.login("/workbench", " ", request));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void shouldReturnBadGatewayWhenUserServiceUnavailable() {
        SsoEntryService entryService = mock(SsoEntryService.class);
        SsoController controller = new SsoController(entryService);
        when(entryService.buildRegisterRedirectUri(anyString(), eq("state-2")))
                .thenThrow(new IllegalStateException("down"));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sso/register");
        request.setScheme("http");
        request.setServerName("ainovel.seekerhut.com");
        request.setServerPort(80);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.register("/workbench", "state-2", request));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatusCode());
    }

    @Test
    void shouldPreferRefererOriginWhenProxyHostIsBackendPort() {
        SsoEntryService entryService = mock(SsoEntryService.class);
        SsoController controller = new SsoController(entryService);
        URI target = URI.create("http://127.0.0.1:10002/sso/login?redirect=r&state=s");
        when(entryService.buildLoginRedirectUri(anyString(), eq("state-3"))).thenReturn(target);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/sso/login");
        request.setScheme("http");
        request.setServerName("127.0.0.1");
        request.setServerPort(11041);
        request.addHeader("Host", "127.0.0.1:11041");
        request.addHeader("Referer", "http://127.0.0.1:11040/login?next=/workbench");

        controller.login("/workbench", "state-3", request);

        ArgumentCaptor<String> callbackCaptor = ArgumentCaptor.forClass(String.class);
        verify(entryService).buildLoginRedirectUri(callbackCaptor.capture(), eq("state-3"));
        String callback = callbackCaptor.getValue();
        assertTrue(callback.startsWith("http://127.0.0.1:11040/sso/callback"));
    }
}
