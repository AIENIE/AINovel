package com.ainovel.app.adminauth;

import com.ainovel.app.security.JwtService;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;
import com.ainovel.app.user.User;
import com.ainovel.app.v2.V2ModelPersistenceService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminEncodedPathSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminSessionService adminSessionService;
    @MockBean
    private JwtService jwtService;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private SsoUserProvisioningService provisioningService;
    @MockBean
    private UserSessionValidator userSessionValidator;
    @MockBean
    private ResourceAccessGuard resourceAccessGuard;
    @MockBean
    private V2ModelPersistenceService persistenceService;

    @Test
    void encodedV1AdminWriteWithoutOriginIsRejectedByAdminFilter() throws Exception {
        mockMvc.perform(post(URI.create("/api/v1/%61dmin/redeem-codes"))
                        .servletPath("/api")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ADMIN_ORIGIN_REJECTED"));
    }

    @Test
    void encodedV2AdminPathRejectsOrdinarySsoRoleAdmin() throws Exception {
        Claims claims = signedUserClaims();
        when(jwtService.parseClaims("signed-sso-admin")).thenReturn(claims);
        when(userSessionValidator.validate(18L, "session-001")).thenReturn(true);
        when(userDetailsService.loadUserByUsername("sso-admin"))
                .thenReturn(org.springframework.security.core.userdetails.User
                        .withUsername("sso-admin")
                        .password("n/a")
                        .authorities("ROLE_ADMIN")
                        .build());

        mockMvc.perform(get(URI.create("/api/v2/%61dmin/model-routing"))
                        .servletPath("/api")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer signed-sso-admin"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("LOCAL_ADMIN_REQUIRED"));

        verify(userSessionValidator).validate(18L, "session-001");
        verify(userDetailsService).loadUserByUsername("sso-admin");
    }

    @Test
    void canonicalV2AdminPathAcceptsOpaqueLocalAdminSession() throws Exception {
        Instant now = Instant.now();
        when(adminSessionService.resolve("opaque-session")).thenReturn(new AdminSessionService.Resolved(
                "session-hash", "local-admin", "FULL", "PASSWORD_TOTP",
                now.minusSeconds(60), now.minusSeconds(30), now.plusSeconds(600)
        ));
        User admin = new User();
        admin.setUsername("admin");
        admin.setRoles(Set.of("ROLE_ADMIN"));
        when(resourceAccessGuard.currentUser(any(UserDetails.class))).thenReturn(admin);
        when(persistenceService.listRouting()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/admin/model-routing")
                        .servletPath("/api")
                        .cookie(new Cookie(AdminAuthConstants.ADMIN_SESSION_COOKIE, "opaque-session")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));

        verify(persistenceService).listRouting();
        verify(jwtService, never()).parseClaims(any());
    }

    private Claims signedUserClaims() {
        Claims claims = org.mockito.Mockito.mock(Claims.class);
        when(claims.getSubject()).thenReturn("sso-admin");
        when(claims.get("uid")).thenReturn(18L);
        when(claims.get("sid")).thenReturn("session-001");
        when(claims.get("role")).thenReturn("ADMIN");
        when(claims.get("local_admin")).thenReturn(false);
        return claims;
    }
}
