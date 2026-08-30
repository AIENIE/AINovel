package com.ainovel.app.user;

import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SsoUserProvisioningServiceTests {

    @Autowired
    private SsoUserProvisioningService provisioningService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectCreditAccountRepository accountRepository;

    @Test
    @Transactional
    void createsUserOnFirstSsoLogin() {
        provisioningService.ensureExistsBestEffort("alice", null, 42L);

        User user = userRepository.findByUsername("alice").orElseThrow();
        assertNotNull(user.getId());
        assertTrue(user.getRoles().contains("ROLE_USER"));
        assertFalse(user.getRoles().contains("ROLE_ADMIN"));
        assertNotNull(user.getEmail());
        assertFalse(user.getEmail().isBlank());
        assertNotNull(user.getPasswordHash());
        assertFalse(user.getPasswordHash().isBlank());
        assertEquals(42L, user.getRemoteUid());
        assertFalse(user.isBanned());
        assertTrue(accountRepository.findByUser(user).isEmpty());
    }

    @Test
    @Transactional
    void updatesRoleWhenAdmin() {
        provisioningService.ensureExistsBestEffort("bob", null, 7L);
        provisioningService.ensureExistsBestEffort("bob", "ADMIN", 7L);

        User user = userRepository.findByUsername("bob").orElseThrow();
        assertTrue(user.getRoles().contains("ROLE_USER"));
        assertTrue(user.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    @Transactional
    void ignoresBlankUsername() {
        long before = userRepository.count();
        provisioningService.ensureExistsBestEffort("   ", "ADMIN", 1L);
        assertEquals(before, userRepository.count());
    }

    @Test
    @Transactional
    void refusesToRebindUsernameToAnotherRemoteIdentity() {
        provisioningService.ensureExistsBestEffort("identity-owner", null, 1001L);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> provisioningService.ensureExistsBestEffort("identity-owner", null, 1002L));

        assertEquals("SSO_IDENTITY_CONFLICT", error.getMessage());
        assertEquals(1001L, userRepository.findByUsername("identity-owner").orElseThrow().getRemoteUid());
    }

    @Test
    @Transactional
    void allowsUsernameChangeOnlyForSameRemoteIdentity() {
        provisioningService.ensureExistsBestEffort("before-rename", null, 1003L);
        provisioningService.ensureExistsBestEffort("after-rename", null, 1003L);

        User renamed = userRepository.findByRemoteUid(1003L).orElseThrow();
        assertEquals("after-rename", renamed.getUsername());
    }
}
