package com.ainovel.app.adminauth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminTrustedOriginFilter extends OncePerRequestFilter {
    private final Set<String> trustedOrigins;

    public AdminTrustedOriginFilter(AdminLocalAuthProperties properties) {
        this.trustedOrigins = Arrays.stream(properties.getTrustedOrigins().split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(this::normalizeConfiguredOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = AdminRequestPaths.normalized(request);
        return !AdminRequestPaths.isUnsafeMethod(request.getMethod())
                || (!AdminRequestPaths.isAdminAuth(path) && !AdminRequestPaths.isAdminBusiness(path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String originHeader = request.getHeader("Origin");
        String origin;
        if (originHeader == null || originHeader.isBlank()) {
            origin = originOf(request.getHeader("Referer"));
        } else {
            origin = originOf(originHeader);
            if (!originHeader.equals(origin)) {
                origin = null;
            }
        }
        if (origin == null || !trustedOrigins.contains(origin)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"code\":\"ADMIN_ORIGIN_REJECTED\",\"message\":\"后台请求来源校验失败\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String normalizeConfiguredOrigin(String raw) {
        String origin = originOf(raw);
        if (origin == null || !origin.equals(raw)) {
            throw new IllegalStateException("ADMIN_TRUSTED_ORIGINS must contain exact origins without paths");
        }
        return origin;
    }

    private String originOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(raw);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder value = new StringBuilder(uri.getScheme().toLowerCase())
                    .append("://")
                    .append(uri.getHost().toLowerCase());
            if (uri.getPort() > 0) {
                value.append(':').append(uri.getPort());
            }
            return value.toString();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
