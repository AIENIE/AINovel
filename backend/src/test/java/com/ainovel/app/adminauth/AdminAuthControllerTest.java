package com.ainovel.app.adminauth;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthControllerTest {

    @Test
    void loginShouldReturnTokenPayloadAndAuditSuccess() {
        AdminLocalAuthService authService = mock(AdminLocalAuthService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(authService, recordFileSink);
        AdminLocalAuthService.LoginResult result = new AdminLocalAuthService.LoginResult(
                "token-1",
                "root",
                Instant.parse("2026-07-06T00:00:00Z")
        );
        when(authService.login("root", "secret")).thenReturn(result);

        ResponseEntity<?> response = controller.login(new AdminAuthController.LoginRequest("root", "secret"));

        assertEquals(200, response.getStatusCode().value());
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertEquals("token-1", body.get("token"));
        assertEquals("root", body.get("username"));
        verify(recordFileSink).appendAudit(argThat(fields ->
                "admin.login".equals(fields.get("action"))
                        && "root".equals(fields.get("actor"))
                        && "SUCCESS".equals(fields.get("result"))
                        && "INFO".equals(fields.get("severity"))
        ));
    }

    @Test
    void loginShouldReturnUnauthorizedForBusinessFailure() {
        AdminLocalAuthService authService = mock(AdminLocalAuthService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(authService, recordFileSink);
        when(authService.login("root", "wrong")).thenThrow(new BusinessException("用户名或密码错误"));

        ResponseEntity<?> response = controller.login(new AdminAuthController.LoginRequest("root", "wrong"));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("用户名或密码错误", ((Map<?, ?>) response.getBody()).get("message"));
        verify(recordFileSink).appendAudit(argThat(fields ->
                "FAILED".equals(fields.get("result"))
                        && "WARN".equals(fields.get("severity"))
        ));
    }

    @Test
    void loginShouldReturnInternalServerErrorForConfigurationFailure() {
        AdminLocalAuthService authService = mock(AdminLocalAuthService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(authService, recordFileSink);
        when(authService.login("root", "secret")).thenThrow(new IllegalStateException("ADMIN_PASSWORD 未配置"));

        ResponseEntity<?> response = controller.login(new AdminAuthController.LoginRequest("root", "secret"));

        assertEquals(500, response.getStatusCode().value());
        assertEquals("ADMIN_PASSWORD 未配置", ((Map<?, ?>) response.getBody()).get("message"));
        verify(recordFileSink).appendAudit(argThat(fields ->
                "FAILED".equals(fields.get("result"))
                        && "ERROR".equals(fields.get("severity"))
        ));
    }

    @Test
    void meShouldDelegatePrincipalUsername() {
        AdminLocalAuthService authService = mock(AdminLocalAuthService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(authService, recordFileSink);
        UserDetails principal = mock(UserDetails.class);
        AdminLocalAuthService.MeResult me = new AdminLocalAuthService.MeResult("root");
        when(principal.getUsername()).thenReturn("root");
        when(authService.me("root")).thenReturn(me);

        ResponseEntity<?> response = controller.me(principal);

        assertSame(me, response.getBody());
    }

    @Test
    void logoutShouldAuditSuccess() {
        AdminLocalAuthService authService = mock(AdminLocalAuthService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(authService, recordFileSink);

        ResponseEntity<?> response = controller.logout();

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, ((Map<?, ?>) response.getBody()).get("success"));
        verify(recordFileSink).appendAudit(argThat(fields ->
                "admin.logout".equals(fields.get("action"))
                        && "admin".equals(fields.get("actor"))
                        && "SUCCESS".equals(fields.get("result"))
        ));
    }
}
