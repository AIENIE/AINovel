package com.ainovel.app.auth;

import com.ainovel.app.integration.ExternalServiceProperties;
import com.ainovel.app.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import javax.net.ssl.SSLHandshakeException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsoTokenExchangeServiceTest {

    @Test
    void certificateFailureFailsClosedWithoutRetryOrCredentialDisclosure() throws Exception {
        HttpClient client = mock(HttpClient.class);
        JwtService jwtService = mock(JwtService.class);
        SsoTokenExchangeService service = service(client, jwtService);
        when(client.send(any(HttpRequest.class), anyStringBodyHandler()))
                .thenThrow(new SSLHandshakeException("certificate rejected; code=private-code"));

        ResponseStatusException failure = assertThrows(ResponseStatusException.class,
                () -> service.exchange("private-code", "https://localainovel.testhut.top/sso/callback"));

        assertEquals("USER_SERVICE_TOKEN_EXCHANGE_FAILED", failure.getReason());
        assertFalse(failure.toString().contains("private-code"));
        verify(client, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        verify(jwtService, never()).generateToken(any(), any(), any(Duration.class));
    }

    @Test
    void standardClientSuccessfulResponseIsLocallySigned() throws Exception {
        HttpClient client = mock(HttpClient.class);
        JwtService jwtService = mock(JwtService.class);
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                {"accessToken":"upstream-token","userId":18,"username":"signed-user",\
                 "sessionId":"session-001","rememberDays":1,"expiresIn":3600}
                """);
        when(client.send(any(HttpRequest.class), anyStringBodyHandler())).thenReturn(response);
        when(jwtService.generateToken(any(), any(), any(Duration.class))).thenReturn("local-signed-token");

        SsoTokenExchangeResponse result = service(client, jwtService).exchange(
                "one-time-code", "https://localainovel.testhut.top/sso/callback"
        );

        assertEquals("local-signed-token", result.accessToken());
        verify(client, times(1)).send(any(HttpRequest.class), anyStringBodyHandler());
        verify(jwtService).generateToken(any(), any(), any(Duration.class));
    }

    private SsoTokenExchangeService service(HttpClient client, JwtService jwtService) {
        ExternalServiceProperties properties = new ExternalServiceProperties();
        properties.getUserserviceHttp().setAddress("https://localuserservice.testhut.top");
        return new SsoTokenExchangeService(
                new SsoEntryService(properties), new ObjectMapper(), jwtService, client
        );
    }

    @SuppressWarnings("unchecked")
    private HttpResponse.BodyHandler<String> anyStringBodyHandler() {
        return any(HttpResponse.BodyHandler.class);
    }
}
