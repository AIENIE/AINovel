package com.ainovel.app.security;

import com.ainovel.app.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public final class AuthenticatedUserPrincipal implements UserDetails {
    private final User user;
    private final List<GrantedAuthority> authorities;

    public AuthenticatedUserPrincipal(User user) {
        this.user = user;
        this.authorities = user.getRoles() == null ? List.of()
                : user.getRoles().stream().map(SimpleGrantedAuthority::new).map(GrantedAuthority.class::cast).toList();
    }

    public User user() { return user; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return user.getPasswordHash() == null ? "" : user.getPasswordHash(); }
    @Override public String getUsername() { return user.getUsername(); }
}
