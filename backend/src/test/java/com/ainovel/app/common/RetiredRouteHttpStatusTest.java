package com.ainovel.app.common;

import com.ainovel.app.adminauth.AdminSessionService;
import com.ainovel.app.adminauth.AdminLocalAuthService;
import com.ainovel.app.auth.SsoTokenExchangeService;
import com.ainovel.app.security.JwtService;
import com.ainovel.app.security.remote.UserSessionValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1",
        "spring.datasource.url=jdbc:h2:mem:retired-route-http;MODE=MySQL"
})
@ActiveProfiles("test")
@Sql(statements = """
        create table if not exists admin_sessions (
          session_id char(64) primary key, subject_id varchar(255) not null,
          scope varchar(32) not null, environment varchar(16) not null,
          auth_mode varchar(16) not null, assurance varchar(32) not null,
          password_authenticated_at timestamp, totp_authenticated_at timestamp,
          credential_key_version varchar(64), password_credential_hash char(64) not null,
          issued_at timestamp not null, expires_at timestamp not null,
          idle_expires_at timestamp not null, last_seen_at timestamp not null, revoked_at timestamp
        );
        """)
class RetiredRouteHttpStatusTest {
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired AdminSessionService adminSessions;
    @MockitoBean UserSessionValidator userSessions;
    @MockitoBean SsoTokenExchangeService ssoExchange;
    @MockitoBean AdminLocalAuthService adminAuth;
    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void authenticatedRetiredRoutesRemain404ThroughTheRealServletContainer() throws Exception {
        when(userSessions.validate(991L, "route-test")).thenReturn(UserSessionValidator.ValidationResult.VALID);
        String token = jwt.generateToken("retired-route-reader", Map.of("uid", 991L, "sid", "route-test", "role", "USER"));
        assertEquals(200, send("GET", "/v1/story-cards", token, null).statusCode());
        String id = "00000000-0000-0000-0000-000000000001";
        for (String path : new String[]{
                "/v1/user/password", "/v1/admin-auth/login/challenge",
                "/v1/manuscripts/" + id + "/sections/analyze-character-changes",
                "/v2/stories/" + id + "/analysis/beta-reader",
                "/v2/stories/" + id + "/analysis/continuity-check"
        }) {
            var response = send("POST", path, token, null);
            assertEquals(404, response.statusCode(), path + " " + response.body());
            assertTrue(response.body().contains("NOT_FOUND"), path);
        }
        // The fixture uses the normal session store; no production auth filter is replaced.
        Instant now = Instant.now();
        String cookie = "AINOVEL_ADMIN_SESSION=" + adminSessions.issue("admin", "FULL", "PASSWORD_TOTP", now, now, "v1").token();
        assertEquals(200, send("GET", "/v1/admin-auth/me", null, cookie).statusCode());
        for (String path : new String[]{"/v1/admin/ops/events", "/v1/admin/ops/audit"}) {
            var response = send("GET", path, null, cookie);
            assertEquals(404, response.statusCode(), path + " " + response.body());
            assertTrue(response.body().contains("NOT_FOUND"), path);
        }
    }

    @Test
    void missingRoutesDoNotBypassAuthenticationOrSessionValidation() throws Exception {
        assertEquals(401, send("POST", "/v1/user/password", null, null).statusCode());
        assertEquals(403, send("GET", "/v1/admin/ops/events", null, null).statusCode());
        when(userSessions.validate(992L, "invalid")).thenReturn(UserSessionValidator.ValidationResult.INVALID);
        String invalid = jwt.generateToken("invalid-route-reader", Map.of("uid", 992L, "sid", "invalid", "role", "USER"));
        assertEquals(401, send("POST", "/v1/user/password", invalid, null).statusCode());
        when(userSessions.validate(992L, "unavailable")).thenReturn(UserSessionValidator.ValidationResult.UNAVAILABLE);
        String unavailable = jwt.generateToken("invalid-route-reader", Map.of("uid", 992L, "sid", "unavailable", "role", "USER"));
        assertEquals(503, send("POST", "/v1/user/password", unavailable, null).statusCode());
    }

    private HttpResponse<String> send(String method, String path, String token, String cookie) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path));
        request.header("Origin", "https://localainovel.testhut.top");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (cookie != null) request.header("Cookie", cookie);
        if (method.equals("POST")) request.header("Content-Type", "application/json");
        request.method(method, method.equals("POST") ? HttpRequest.BodyPublishers.ofString("{}") : HttpRequest.BodyPublishers.noBody());
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
