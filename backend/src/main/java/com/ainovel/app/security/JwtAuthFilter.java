package com.ainovel.app.security;

import com.ainovel.app.common.SafeLogThrowable;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;
import com.ainovel.app.adminauth.AdminSessionService;
import com.ainovel.app.adminauth.AdminAuthController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private SsoUserProvisioningService provisioningService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ObjectProvider<UserSessionValidator> userSessionValidatorProvider;
    @Autowired
    private AdminSessionService adminSessionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String token = tokenFromRequest(request, authHeader);
        boolean fromAdminCookie = authHeader == null && token != null;
        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            VerifiedClaims verifiedClaims = parseVerifiedClaims(token);
            ParsedTokenClaims parsed = verifiedClaims == null ? null : verifiedClaims.claims();
            boolean verifiedBySignature = parsed != null;
            if (parsed == null && !fromAdminCookie) {
                parsed = parseUnverifiedClaims(token);
                verifiedBySignature = false;
            }
            if (parsed == null || parsed.username() == null || parsed.username().isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            UserSessionValidator validator = userSessionValidatorProvider.getIfAvailable();
            if (verifiedBySignature) {
                boolean localAdminToken = verifiedClaims != null && verifiedClaims.localAdminToken();
                if (localAdminToken && (adminSessionService == null || !adminSessionService.isActive(parsed.adminSessionId(), parsed.adminScope()))) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (!localAdminToken && validator != null && !isSessionValid(validator, parsed.uid(), parsed.sid())) {
                    filterChain.doFilter(request, response);
                    return;
                }
            } else {
                if (validator == null || !isTokenNotExpired(parsed.expEpochSeconds()) || !isSessionValid(validator, parsed.uid(), parsed.sid())) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            boolean localAdminToken = verifiedClaims != null && verifiedClaims.localAdminToken();
            if (!localAdminToken) {
                try { provisioningService.ensureExistsBestEffort(parsed.username(), parsed.role(), parsed.uid()); } catch (Exception ignored) { }
            }

            UserDetails userDetails;
            if (localAdminToken) {
                String authority = "RECOVERY".equals(parsed.adminScope()) ? "ADMIN_RECOVERY" : "ROLE_ADMIN";
                userDetails = org.springframework.security.core.userdetails.User.withUsername(parsed.username())
                        .password("n/a").authorities(authority).build();
            } else {
                try { userDetails = userDetailsService.loadUserByUsername(parsed.username()); }
                catch (Exception e) { filterChain.doFilter(request, response); return; }
            }
            List<GrantedAuthority> authorities = new ArrayList<>(userDetails.getAuthorities());
            if (localAdminToken && "RECOVERY".equals(parsed.adminScope()) && authorities.stream().noneMatch(x -> x.getAuthority().equals("ADMIN_RECOVERY"))) authorities.add(new SimpleGrantedAuthority("ADMIN_RECOVERY"));
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, authorities);
            authToken.setDetails(localAdminToken ? parsed.adminSessionId() : new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            log.warn("JWT validation failed errorType={}",
                    e.getClass().getSimpleName(), SafeLogThrowable.stackOnly(e));
        }
        filterChain.doFilter(request, response);
    }

    private String tokenFromRequest(HttpServletRequest request, String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AdminAuthController.ADMIN_SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private VerifiedClaims parseVerifiedClaims(String token) {
        try {
            Claims claims = jwtService.parseClaims(token);
            ParsedTokenClaims parsed = mapClaims(claims.getSubject(), claims.get("uid"), claims.get("sid"), claims.get("role"), claims.get("exp"), claims.get("admin_session"), claims.get("admin_scope"));
            Object localAdminRaw = claims.get("local_admin");
            boolean localAdminToken = Boolean.TRUE.equals(localAdminRaw)
                    || (localAdminRaw instanceof String text && "true".equalsIgnoreCase(text.trim()));
            return new VerifiedClaims(parsed, localAdminToken);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ParsedTokenClaims parseUnverifiedClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length < 2) {
                return null;
            }
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            JsonNode payload = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
            return mapClaims(
                    payload.path("sub").asText(null),
                    payload.get("uid"),
                    payload.get("sid"),
                    payload.get("role"),
                    payload.get("exp"), payload.get("admin_session"), payload.get("admin_scope")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private ParsedTokenClaims mapClaims(String username, Object uidRaw, Object sidRaw, Object roleRaw, Object expRaw, Object adminSessionRaw, Object adminScopeRaw) {
        Long uid = null;
        if (uidRaw instanceof Number number) {
            uid = number.longValue();
        } else if (uidRaw instanceof String str) {
            try {
                uid = Long.parseLong(str);
            } catch (NumberFormatException ignored) {
            }
        } else if (uidRaw instanceof JsonNode node && node.isNumber()) {
            uid = node.longValue();
        } else if (uidRaw instanceof JsonNode node && node.isTextual()) {
            try {
                uid = Long.parseLong(node.asText());
            } catch (NumberFormatException ignored) {
            }
        }

        String sid = null;
        if (sidRaw instanceof String str) {
            sid = str;
        } else if (sidRaw instanceof JsonNode node && node.isTextual()) {
            sid = node.asText();
        }

        String role = null;
        if (roleRaw instanceof String str) {
            role = str;
        } else if (roleRaw instanceof JsonNode node && node.isTextual()) {
            role = node.asText();
        }

        Long expEpochSeconds = null;
        if (expRaw instanceof Number number) {
            expEpochSeconds = number.longValue();
        } else if (expRaw instanceof JsonNode node && node.isNumber()) {
            expEpochSeconds = node.longValue();
        }

        String adminSession = adminSessionRaw instanceof String text ? text : adminSessionRaw instanceof JsonNode node && node.isTextual() ? node.asText() : null;
        String adminScope = adminScopeRaw instanceof String text ? text : adminScopeRaw instanceof JsonNode node && node.isTextual() ? node.asText() : null;
        return new ParsedTokenClaims(username, uid, sid, role, expEpochSeconds, adminSession, adminScope);
    }

    private boolean isSessionValid(UserSessionValidator validator, Long uid, String sid) {
        return uid != null && uid > 0 && sid != null && !sid.isBlank() && validator.validate(uid, sid);
    }

    private boolean isTokenNotExpired(Long expEpochSeconds) {
        return expEpochSeconds != null && expEpochSeconds > Instant.now().getEpochSecond();
    }

    private record ParsedTokenClaims(String username, Long uid, String sid, String role, Long expEpochSeconds, String adminSessionId, String adminScope) {
    }

    private record VerifiedClaims(ParsedTokenClaims claims, boolean localAdminToken) {
    }
}
