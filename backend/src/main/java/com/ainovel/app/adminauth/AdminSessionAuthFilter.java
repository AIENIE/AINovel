package com.ainovel.app.adminauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AdminSessionAuthFilter extends OncePerRequestFilter {
    private final AdminSessionService sessions;
    private final AdminLocalAuthProperties properties;

    public AdminSessionAuthFilter(AdminSessionService sessions, AdminLocalAuthProperties properties) {
        this.sessions = sessions;
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = AdminRequestPaths.normalized(request);
        return !AdminRequestPaths.isAdminAuth(path) && !AdminRequestPaths.isAdminBusiness(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = cookie(request, AdminAuthConstants.ADMIN_SESSION_COOKIE);
            AdminSessionService.Resolved resolved = sessions.resolve(token);
            if (resolved != null) {
                String authority = "RECOVERY".equals(resolved.scope())
                        ? AdminAuthConstants.RECOVERY_AUTHORITY
                        : AdminAuthConstants.FULL_AUTHORITY;
                UserDetails principal = User.withUsername(properties.getUsername())
                        .password("n/a")
                        .authorities(authority)
                        .build();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                authentication.setDetails(resolved.sessionHash());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        filterChain.doFilter(request, response);
    }

    private String cookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
