package com.ainovel.app.adminauth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminOperationProofServiceTest {
    @Test
    void issuedProofIsHashedAndBoundToUserSessionActionAndTarget() {
        AdminAuthStore store = mock(AdminAuthStore.class);
        AdminLocalAuthService auth = mock(AdminLocalAuthService.class);
        AdminRateLimiter limiter = allowingLimiter();
        AdminOperationProofService service = new AdminOperationProofService(policy(), store, auth, limiter);
        String challengeId = "raw-challenge";
        String challengeHash = AdminLocalAuthService.hash(challengeId);
        when(store.operationChallenge(challengeHash)).thenReturn(Optional.of(new AdminAuthStore.OperationChallenge(
                challengeHash, "configured-admin", "session-a", "DELETE:/v1/admin/items/1",
                "/v1/admin/items/1#body", Instant.now().plusSeconds(120), 0, null
        )));
        when(store.incrementOperationAttempt(challengeHash)).thenReturn(true);
        when(auth.verifyCurrentTotp("123456")).thenReturn(true);
        when(store.consumeOperationChallenge(challengeHash)).thenReturn(true);

        AdminOperationProofService.ProofVerification issued = service.verifyAndIssue(
                challengeId, "session-a", "123456", "127.0.0.1"
        );

        ArgumentCaptor<String> storedHash = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Instant> expiry = ArgumentCaptor.forClass(Instant.class);
        verify(store).insertOperationProof(
                storedHash.capture(),
                org.mockito.ArgumentMatchers.eq("configured-admin"),
                org.mockito.ArgumentMatchers.eq("session-a"),
                org.mockito.ArgumentMatchers.eq("DELETE:/v1/admin/items/1"),
                org.mockito.ArgumentMatchers.eq("/v1/admin/items/1#body"),
                expiry.capture()
        );
        assertNotEquals(issued.proofToken(), storedHash.getValue());
        assertEquals(AdminLocalAuthService.hash(issued.proofToken()), storedHash.getValue());
        assertEquals(60, issued.expiresInSeconds());
        assertTrue(expiry.getValue().isAfter(Instant.now().plusSeconds(55)));

        when(store.consumeOperationProof(
                storedHash.getValue(), "configured-admin", "session-a",
                "DELETE:/v1/admin/items/1", "/v1/admin/items/1#body"
        )).thenReturn(true);
        assertTrue(service.consume(
                issued.proofToken(), "configured-admin", "session-a",
                "DELETE:/v1/admin/items/1", "/v1/admin/items/1#body"
        ));
    }

    @Test
    void aChallengeCannotBeVerifiedFromAnotherSession() {
        AdminAuthStore store = mock(AdminAuthStore.class);
        AdminLocalAuthService auth = mock(AdminLocalAuthService.class);
        AdminOperationProofService service = new AdminOperationProofService(policy(), store, auth, allowingLimiter());
        String challengeHash = AdminLocalAuthService.hash("raw-challenge");
        when(store.operationChallenge(challengeHash)).thenReturn(Optional.of(new AdminAuthStore.OperationChallenge(
                challengeHash, "configured-admin", "session-a", "PUT:/v1/admin/system-config",
                "/v1/admin/system-config#body", Instant.now().plusSeconds(120), 0, null
        )));

        assertThrows(AdminAuthenticationException.class, () -> service.verifyAndIssue(
                "raw-challenge", "session-b", "123456", "127.0.0.1"
        ));

        verify(auth, never()).verifyCurrentTotp(anyString());
        verify(store, never()).insertOperationProof(anyString(), anyString(), anyString(), anyString(), anyString(), any());
    }

    private AdminRateLimiter allowingLimiter() {
        AdminRateLimiter limiter = mock(AdminRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        return limiter;
    }

    private AdminAuthPolicySource policy() {
        return new AdminAuthPolicySource() {
            @Override public String env() { return "test"; }
            @Override public String authMode() { return "totp"; }
        };
    }
}
