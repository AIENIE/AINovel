package com.ainovel.app.common;

import com.ainovel.app.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentUserResolverTest {

    @Test
    void requireShouldReturnMatchedUser() {
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUserResolver resolver = new CurrentUserResolver(userRepository);
        com.ainovel.app.user.User domainUser = new com.ainovel.app.user.User();
        domainUser.setId(UUID.randomUUID());
        domainUser.setUsername("demo");
        domainUser.setRoles(Set.of("ROLE_USER"));
        UserDetails principal = User.withUsername("demo").password("n/a").authorities("ROLE_USER").build();
        when(userRepository.findByUsername("demo")).thenReturn(Optional.of(domainUser));

        com.ainovel.app.user.User resolved = resolver.require(principal);

        assertEquals(domainUser, resolved);
    }

    @Test
    void requireShouldRejectMissingPrincipal() {
        CurrentUserResolver resolver = new CurrentUserResolver(mock(UserRepository.class));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> resolver.require(null));

        assertEquals("未登录", ex.getMessage());
    }

    @Test
    void requireShouldRejectUnknownUser() {
        UserRepository userRepository = mock(UserRepository.class);
        CurrentUserResolver resolver = new CurrentUserResolver(userRepository);
        UserDetails principal = User.withUsername("ghost").password("n/a").authorities("ROLE_USER").build();
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> resolver.require(principal));

        assertEquals("用户不存在", ex.getMessage());
    }
}
