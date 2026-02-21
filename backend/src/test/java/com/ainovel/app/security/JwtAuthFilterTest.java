package com.ainovel.app.security;

import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

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
    void shouldAuthenticateWithExternalTokenWhenSessionValidationPasses() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        doThrow(new RuntimeException("bad-signature")).when(jwtService).parseClaims(anyString());

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("goodboy95"))
                .thenReturn(User.withUsername("goodboy95").password("n/a").authorities("ROLE_USER").build());

        SsoUserProvisioningService provisioningService = mock(SsoUserProvisioningService.class);
        UserSessionValidator validator = mock(UserSessionValidator.class);
        when(validator.validate(18L, "sid-001")).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserSessionValidator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(validator);

        JwtAuthFilter filter = createFilter(jwtService, userDetailsService, provisioningService, provider);
        String token = jwtToken(Map.of(
                "sub", "goodboy95",
                "uid", 18,
                "sid", "sid-001",
                "role", "USER",
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("goodboy95", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(provisioningService).ensureExistsBestEffort("goodboy95", "USER", 18L);
    }

    @Test
    void shouldRejectExternalTokenWhenSessionValidatorUnavailable() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        doThrow(new RuntimeException("bad-signature")).when(jwtService).parseClaims(anyString());

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        SsoUserProvisioningService provisioningService = mock(SsoUserProvisioningService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserSessionValidator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        JwtAuthFilter filter = createFilter(jwtService, userDetailsService, provisioningService, provider);
        String token = jwtToken(Map.of(
                "sub", "goodboy95",
                "uid", 18,
                "sid", "sid-001",
                "role", "USER",
                "exp", Instant.now().plusSeconds(3600).getEpochSecond()
        ));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(provisioningService, never()).ensureExistsBestEffort(anyString(), anyString(), anyLong());
    }

    @Test
    void shouldKeepSignedTokenPath() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("signed-user");
        when(claims.get("uid")).thenReturn(99L);
        when(claims.get("sid")).thenReturn("sid-signed");
        when(claims.get("role")).thenReturn("USER");
        when(jwtService.parseClaims(anyString())).thenReturn(claims);

        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        when(userDetailsService.loadUserByUsername("signed-user"))
                .thenReturn(User.withUsername("signed-user").password("n/a").authorities("ROLE_USER").build());

        SsoUserProvisioningService provisioningService = mock(SsoUserProvisioningService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<UserSessionValidator> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        JwtAuthFilter filter = createFilter(jwtService, userDetailsService, provisioningService, provider);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals("signed-user", SecurityContextHolder.getContext().getAuthentication().getName());
        verify(provisioningService).ensureExistsBestEffort("signed-user", "USER", 99L);
    }

    private JwtAuthFilter createFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            SsoUserProvisioningService provisioningService,
            ObjectProvider<UserSessionValidator> provider
    ) {
        JwtAuthFilter filter = new JwtAuthFilter();
        ReflectionTestUtils.setField(filter, "jwtService", jwtService);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
        ReflectionTestUtils.setField(filter, "provisioningService", provisioningService);
        ReflectionTestUtils.setField(filter, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(filter, "userSessionValidatorProvider", provider);
        return filter;
    }

    private String jwtToken(Map<String, Object> payload) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String body = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mapper.writeValueAsBytes(payload));
        return header + "." + body + ".signature";
    }
}
