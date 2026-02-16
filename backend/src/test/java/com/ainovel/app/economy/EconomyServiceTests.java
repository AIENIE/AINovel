package com.ainovel.app.economy;

import com.ainovel.app.integration.BillingGrpcClient;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EconomyServiceTests {

    @Mock
    private BillingGrpcClient billingGrpcClient;
    @Mock
    private UserRepository userRepository;

    private EconomyService economyService;

    @BeforeEach
    void setUp() {
        economyService = new EconomyService(billingGrpcClient, userRepository);
    }

    @Test
    void checkIn_shouldUseRemoteBillingAndPersistSnapshot() {
        User user = new User();
        user.setRemoteUid(1001L);
        user.setCredits(100);
        Instant last = Instant.parse("2026-02-16T08:00:00Z");

        when(billingGrpcClient.checkin(1001L))
                .thenReturn(new BillingGrpcClient.CheckinResult(true, 500, 1600, false, ""));
        when(billingGrpcClient.checkinStatus(1001L))
                .thenReturn(new BillingGrpcClient.CheckinStatus(true, last, 500));

        EconomyService.CreditChangeResult result = economyService.checkIn(user);

        assertTrue(result.success());
        assertEquals(500.0, result.points());
        assertEquals(1600.0, result.newTotal());
        assertEquals(last, result.lastCheckInAt());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertEquals(1600.0, userCaptor.getValue().getCredits());
        assertEquals(last, userCaptor.getValue().getLastCheckInAt());
    }

    @Test
    void checkIn_shouldFallbackWhenRemoteUidMissing() {
        User user = new User();
        user.setCredits(80.0);

        EconomyService.CreditChangeResult result = economyService.checkIn(user);

        assertFalse(result.success());
        assertEquals(0.0, result.points());
        assertEquals(80.0, result.newTotal());
        verifyNoInteractions(billingGrpcClient);
        verify(userRepository, never()).save(any());
    }

    @Test
    void redeem_shouldThrowWhenBillingReportsFailure() {
        User user = new User();
        user.setRemoteUid(1001L);

        when(billingGrpcClient.redeem(1001L, "INVALID"))
                .thenReturn(new BillingGrpcClient.RedeemResult(false, 0, 1000, "INVALID_CODE"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> economyService.redeem(user, "INVALID"));
        assertEquals("INVALID_CODE", ex.getMessage());
        verify(userRepository, never()).save(any());
    }
}
