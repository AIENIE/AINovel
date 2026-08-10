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
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Service
public class SsoTokenExchangeService {

    private final SsoEntryService ssoEntryService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final HttpClient localInsecureHttpClient;
    private final JwtService jwtService;

    @Autowired
    public SsoTokenExchangeService(SsoEntryService ssoEntryService, ObjectMapper objectMapper, JwtService jwtService) {
        this.ssoEntryService = ssoEntryService;
        this.objectMapper = objectMapper;
        this.jwtService = jwtService;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.localInsecureHttpClient = buildLocalInsecureHttpClient();
    }

    // Package-visible compatibility constructor for transport-only unit tests.
    public SsoTokenExchangeService(SsoEntryService ssoEntryService, ObjectMapper objectMapper) {
        this.ssoEntryService = ssoEntryService;
        this.objectMapper = objectMapper;
        this.jwtService = null;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.localInsecureHttpClient = buildLocalInsecureHttpClient();
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
            HttpResponse<String> response = send(request, endpoint);
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
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "USER_SERVICE_TOKEN_EXCHANGE_FAILED", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "USER_SERVICE_TOKEN_EXCHANGE_INTERRUPTED", ex);
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

    private HttpResponse<String> send(HttpRequest request, URI endpoint) throws IOException, InterruptedException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            if (!allowsLocalInsecureTls(endpoint)) {
                throw ex;
            }
            return localInsecureHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private boolean allowsLocalInsecureTls(URI endpoint) {
        String scheme = endpoint.getScheme();
        String host = endpoint.getHost();
        return "https".equalsIgnoreCase(scheme)
                && host != null
                && ("localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || host.endsWith(".localhut.com")
                || host.endsWith(".testhut.top"));
    }

    private HttpClient buildLocalInsecureHttpClient() {
        try {
            TrustManager[] trustAll = new TrustManager[] {
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, trustAll, new SecureRandom());
            return HttpClient.newBuilder()
                    .sslContext(context)
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize local SSO TLS fallback", ex);
        }
    }
}
