package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;
import com.ainovel.app.admin.ops.OpsRecordFileSink;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminAuthControllerTest {
    @Test
    void totpUsesSixDigitsAndRejectsMalformedCodes() {
        String secret = "JBSWY3DPEHPK3PXP";
        String code = TotpService.code(secret, 0, 6);
        assertEquals(6, code.length());
        assertTrue(TotpService.verify(secret, code, 0, -1, 6, 30));
        assertFalse(TotpService.verify(secret, "12", 0, -1, 6, 30));
    }

    @Test
    void totpWindowDoesNotAllowReplayOfAcceptedTimestep() {
        String secret = "JBSWY3DPEHPK3PXP";
        String code = TotpService.code(secret, 100, 6);
        assertEquals(-1, TotpService.matchingTimestep(secret, code, 3000, 100, 6, 30));
        assertEquals(100, TotpService.matchingTimestep(secret, code, 3000, 99, 6, 30));
    }

    @Test
    void cryptoRoundTripsEncryptedSecret() {
        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setEncryptionKeys("v1:" + Base64.getEncoder().encodeToString(new byte[32]));
        properties.setActiveKeyVersion("v1");
        AdminAuthCrypto crypto = new AdminAuthCrypto(properties);
        AdminAuthCrypto.EncryptedValue encrypted = crypto.encrypt("secret", "v1");
        assertEquals("secret", crypto.decrypt(encrypted.ciphertext(), encrypted.nonce(), encrypted.keyVersion()));
        assertNotEquals("secret", encrypted.ciphertext());
    }

    @Test
    void authenticationFailuresUseTheSameNoStoreUnauthorizedResponse() {
        AdminLocalAuthService service = mock(AdminLocalAuthService.class);
        OpsRecordFileSink records = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(service, records);

        var response = controller.authenticationFailure(new MockHttpServletRequest());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("no-store", response.getHeaders().getCacheControl());
        verify(records).appendAudit(any());
    }

    @Test
    void successfulTotpLoginSetsAnHttpOnlyAdminCookieWithoutReturningTheToken() {
        AdminLocalAuthService service = mock(AdminLocalAuthService.class);
        OpsRecordFileSink records = mock(OpsRecordFileSink.class);
        AdminAuthController controller = new AdminAuthController(service, records);
        when(service.loginTotp(any(), any(), any())).thenReturn(new AdminLocalAuthService.LoginResult(
                "signed-admin-jwt",
                "admin",
                "FULL",
                Instant.now().plusSeconds(60),
                List.of()
        ));

        var response = controller.loginTotp(
                new AdminAuthController.CodeRequest("challenge", "123456"),
                new MockHttpServletRequest()
        );

        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("AINOVEL_ADMIN_SESSION=signed-admin-jwt"));
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("HttpOnly"));
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("Secure"));
        assertTrue(response.getHeaders().getFirst("Set-Cookie").contains("SameSite=Strict"));
        assertInstanceOf(AdminAuthController.AuthenticatedSession.class, response.getBody());
    }
}
