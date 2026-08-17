package com.ainovel.app.security;

import com.ainovel.app.user.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class SystemGuardFilter extends OncePerRequestFilter {
    @Autowired
    private MaintenanceModeCache maintenanceModeCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            filterChain.doFilter(request, response);
            return;
        }

        User user = auth.getPrincipal() instanceof AuthenticatedUserPrincipal principal ? principal.user() : null;
        if (user == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (user.isBanned()) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"账号已被封禁\"}");
            return;
        }

        boolean maintenance = maintenanceModeCache.enabled();
        if (maintenance && !user.hasRole("ROLE_ADMIN")) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"系统维护中\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
