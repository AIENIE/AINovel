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
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean recovery = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(value -> AdminAuthConstants.RECOVERY_AUTHORITY.equals(value.getAuthority()));
        String path = AdminRequestPaths.normalized(request);
        boolean allowed = ("GET".equals(request.getMethod()) && ("/v1/admin-auth/me".equals(path) || "/v1/admin-auth/bootstrap".equals(path)))
                || ("POST".equals(request.getMethod()) && ("/v1/admin-auth/rebind/start".equals(path) || "/v1/admin-auth/rebind/confirm".equals(path) || "/v1/admin-auth/logout".equals(path)));
        if (recovery && !allowed) {
            response.setStatus(403);
            response.setHeader("Cache-Control", "no-store");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\"RECOVERY_SCOPE_REQUIRED\",\"message\":\"恢复会话仅允许重新绑定动态码或退出\"}");
            return;
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
