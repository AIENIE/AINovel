package com.ainovel.app.adminauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class AdminAccessFiltersTest {
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ordinarySsoRoleAdminCannotEnterLocalAdminBackend() throws Exception {
        AdminBusinessAccessFilter filter = new AdminBusinessAccessFilter(
                policy("local", "password"), mock(AdminOperationProofService.class), new ObjectMapper()
        );
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "sso-admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/v1/admin/dashboard"), response, new MockFilterChain());

        assertEquals(403, response.getStatus());
    }

    @Test
    void highRiskTotpWriteReturnsBoundOperationChallenge() throws Exception {
        AdminOperationProofService proofs = mock(AdminOperationProofService.class);
        when(proofs.consume(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(false);
        when(proofs.createChallenge(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AdminOperationProofService.OperationChallenge(
                        "challenge", Instant.now().plusSeconds(120)
                ));
        AdminBusinessAccessFilter filter = new AdminBusinessAccessFilter(
                policy("local", "totp"), proofs, new ObjectMapper()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority(AdminAuthConstants.FULL_AUTHORITY))
        );
        authentication.setDetails("session-hash");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("PUT", "/v1/admin/system-config"), response, new MockFilterChain());

        assertEquals(428, response.getStatus());
    }

    @Test
    void adminWritesRequireAnExactTrustedOrigin() throws Exception {
        AdminLocalAuthProperties properties = new AdminLocalAuthProperties();
        properties.setTrustedOrigins("https://localainovel.testhut.top");
        AdminTrustedOriginFilter filter = new AdminTrustedOriginFilter(properties);
        MockHttpServletRequest missing = new MockHttpServletRequest("POST", "/v1/admin-auth/login");
        MockHttpServletResponse rejected = new MockHttpServletResponse();
        filter.doFilter(missing, rejected, new MockFilterChain());
        assertEquals(403, rejected.getStatus());

        MockHttpServletRequest trusted = new MockHttpServletRequest("POST", "/v1/admin-auth/login");
        trusted.addHeader("Origin", "https://localainovel.testhut.top");
        MockHttpServletResponse accepted = new MockHttpServletResponse();
        filter.doFilter(trusted, accepted, new MockFilterChain());
        assertEquals(200, accepted.getStatus());

        MockHttpServletRequest pathSpoof = new MockHttpServletRequest("POST", "/v1/admin-auth/login");
        pathSpoof.addHeader("Origin", "https://localainovel.testhut.top/attacker-controlled-path");
        MockHttpServletResponse pathSpoofRejected = new MockHttpServletResponse();
        filter.doFilter(pathSpoof, pathSpoofRejected, new MockFilterChain());
        assertEquals(403, pathSpoofRejected.getStatus());
    }

    @Test
    void operationProofTargetIncludesTheExactRequestBody() throws Exception {
        AdminOperationProofService proofs = mock(AdminOperationProofService.class);
        when(proofs.createChallenge(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new AdminOperationProofService.OperationChallenge(
                        "challenge", Instant.now().plusSeconds(120)
                ));
        AdminBusinessAccessFilter filter = new AdminBusinessAccessFilter(
                policy("local", "totp"), proofs, new ObjectMapper()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority(AdminAuthConstants.FULL_AUTHORITY))
        );
        authentication.setDetails("session-hash");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        MockHttpServletRequest first = new MockHttpServletRequest("POST", "/v1/admin/credits/grant");
        first.setContentType("application/json");
        first.setContent("{\"userId\":\"user-1\",\"amount\":10}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        filter.doFilter(first, new MockHttpServletResponse(), new MockFilterChain());
        MockHttpServletRequest second = new MockHttpServletRequest("POST", "/v1/admin/credits/grant");
        second.setContentType("application/json");
        second.setContent("{\"userId\":\"user-2\",\"amount\":10}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        filter.doFilter(second, new MockHttpServletResponse(), new MockFilterChain());

        ArgumentCaptor<String> targets = ArgumentCaptor.forClass(String.class);
        verify(proofs, org.mockito.Mockito.times(2)).createChallenge(
                eq(AdminLocalAuthService.SUBJECT), eq("session-hash"),
                eq("POST:/v1/admin/credits/grant"), targets.capture(), anyString()
        );
        assertNotEquals(targets.getAllValues().get(0), targets.getAllValues().get(1));
    }

    @Test
    void passwordModeBypassesOperationProofForHighRiskWrites() throws Exception {
        AdminOperationProofService proofs = mock(AdminOperationProofService.class);
        AdminBusinessAccessFilter filter = new AdminBusinessAccessFilter(
                policy("local", "password"), proofs, new ObjectMapper()
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(new SimpleGrantedAuthority(AdminAuthConstants.FULL_AUTHORITY))
        );
        authentication.setDetails("session-hash");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("PUT", "/v1/admin/system-config"), response, new MockFilterChain());

        assertEquals(200, response.getStatus());
        verify(proofs, never()).createChallenge(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private AdminAuthPolicySource policy(String env, String mode) {
        return new AdminAuthPolicySource() {
            @Override public String env() { return env; }
            @Override public String authMode() { return mode; }
        };
    }
}
