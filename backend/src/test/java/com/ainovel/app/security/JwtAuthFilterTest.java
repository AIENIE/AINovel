package com.ainovel.app.security;

import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOnlySignedUserTokensWithValidatedRemoteSession() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = claims("goodboy95", 18L, "sid-001", "USER", false);
        when(jwtService.parseClaims("signed-token")).thenReturn(claims);
        UserDetailsService users = mock(UserDetailsService.class);
        when(users.loadUserByUsername("goodboy95"))
                .thenReturn(User.withUsername("goodboy95").password("n/a").authorities("ROLE_USER").build());
        SsoUserProvisioningService provisioning = mock(SsoUserProvisioningService.class);
        UserSessionValidator validator = mock(UserSessionValidator.class);
        when(validator.validate(18L, "sid-001")).thenReturn(true);
        ObjectProvider<UserSessionValidator> provider = provider(validator);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, users, provisioning, provider);

        MockHttpServletRequest request = bearer("signed-token");
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertEquals("goodboy95", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(provisioning).ensureExistsBestEffort("goodboy95", "USER", 18L);
        verify(validator).validate(18L, "sid-001");
    }

    @Test
    void rejectsForgedUnsignedRoleAdminPayloadWithoutParsingFallback() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        doThrow(new RuntimeException("bad-signature")).when(jwtService).parseClaims(anyString());
        UserDetailsService users = mock(UserDetailsService.class);
        SsoUserProvisioningService provisioning = mock(SsoUserProvisioningService.class);
        UserSessionValidator validator = mock(UserSessionValidator.class);
        when(validator.validate(anyLong(), anyString())).thenReturn(true);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, users, provisioning, provider(validator));

        filter.doFilter(bearer("eyJhbGciOiJub25lIn0.eyJyb2xlIjoiQURNSU4iLCJ1aWQiOjE4LCJzaWQiOiJzaWQtMDAxIn0."),
                new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(validator, never()).validate(anyLong(), anyString());
        verify(provisioning, never()).ensureExistsBestEffort(anyString(), anyString(), anyLong());
    }

    @Test
    void rejectsLegacySignedLocalAdminJwtBecauseAdminUsesOpaqueServerSession() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims legacyAdminClaims = claims("configured-admin", 0L, "admin-session", "ADMIN", true);
        when(jwtService.parseClaims("legacy-admin-token")).thenReturn(legacyAdminClaims);
        UserDetailsService users = mock(UserDetailsService.class);
        SsoUserProvisioningService provisioning = mock(SsoUserProvisioningService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, users, provisioning, provider(null));

        filter.doFilter(bearer("legacy-admin-token"), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(provisioning, never()).ensureExistsBestEffort(anyString(), anyString(), anyLong());
    }

    private Claims claims(String username, long uid, String sid, String role, boolean localAdmin) {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn(username);
        when(claims.get("uid")).thenReturn(uid);
        when(claims.get("sid")).thenReturn(sid);
        when(claims.get("role")).thenReturn(role);
        when(claims.get("local_admin")).thenReturn(localAdmin);
        return claims;
    }

    private MockHttpServletRequest bearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<UserSessionValidator> provider(UserSessionValidator validator) {
        ObjectProvider<UserSessionValidator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(validator);
        return provider;
    }
}
