package com.ainovel.app.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ainovel.app.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Service
public class SsoTokenExchangeService {

    private final SsoEntryService ssoEntryService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final JwtService jwtService;

    @Autowired
    public SsoTokenExchangeService(SsoEntryService ssoEntryService, ObjectMapper objectMapper, JwtService jwtService) {
        this(ssoEntryService, objectMapper, jwtService, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    // Package-visible compatibility constructor for transport-only unit tests.
    public SsoTokenExchangeService(SsoEntryService ssoEntryService, ObjectMapper objectMapper) {
        this(ssoEntryService, objectMapper, null, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build());
    }

    SsoTokenExchangeService(
            SsoEntryService ssoEntryService,
            ObjectMapper objectMapper,
            JwtService jwtService,
            HttpClient httpClient
    ) {
        this.ssoEntryService = ssoEntryService;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.httpClient = httpClient;
    }

    public SsoTokenExchangeResponse exchange(String code, String redirect) {
        if (!StringUtils.hasText(code) || !StringUtils.hasText(redirect)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SSO_CODE_OR_REDIRECT_REQUIRED");
        }
        URI endpoint = ssoEntryService.buildTokenEndpointUri();
        String body = "code=" + form(code.trim()) + "&redirect=" + form(redirect.trim());
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SSO_CODE");
            }
            SsoTokenExchangeResponse payload = objectMapper.readValue(response.body(), SsoTokenExchangeResponse.class);
            if (!StringUtils.hasText(payload.accessToken()) || payload.userId() == null || !StringUtils.hasText(payload.sessionId())) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "INVALID_SSO_TOKEN_RESPONSE");
            }
            if (jwtService == null || !StringUtils.hasText(payload.username())) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "LOCAL_SSO_SIGNER_UNAVAILABLE");
            }
            Duration lifetime = sessionLifetime(payload.expiresIn());
            String localAccessToken = jwtService.generateToken(
                    payload.username().trim(),
                    Map.of(
                            "uid", payload.userId(),
                            "sid", payload.sessionId().trim(),
                            "role", "USER"
                    ),
                    lifetime
            );
            return new SsoTokenExchangeResponse(
                    localAccessToken,
                    payload.userId(),
                    payload.username(),
                    payload.sessionId(),
                    payload.rememberDays(),
                    lifetime.toSeconds()
            );
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "USER_SERVICE_TOKEN_EXCHANGE_FAILED");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "USER_SERVICE_TOKEN_EXCHANGE_INTERRUPTED");
        }
    }

    private Duration sessionLifetime(Long expiresInSeconds) {
        long seconds = expiresInSeconds == null ? Duration.ofHours(2).toSeconds() : expiresInSeconds;
        seconds = Math.max(Duration.ofMinutes(5).toSeconds(), seconds);
        seconds = Math.min(Duration.ofDays(30).toSeconds(), seconds);
        return Duration.ofSeconds(seconds);
    }

    private String form(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

}
