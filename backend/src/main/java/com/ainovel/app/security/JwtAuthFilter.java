package com.ainovel.app.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);
        try {
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            ParsedTokenClaims parsed = parseVerifiedClaims(token);
            boolean verifiedBySignature = parsed != null;
            if (parsed == null) {
                parsed = parseUnverifiedClaims(token);
                verifiedBySignature = false;
            }
            if (parsed == null || parsed.username() == null || parsed.username().isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            UserSessionValidator validator = userSessionValidatorProvider.getIfAvailable();
            if (verifiedBySignature) {
                if (validator != null && !isSessionValid(validator, parsed.uid(), parsed.sid())) {
                    filterChain.doFilter(request, response);
                    return;
                }
            } else {
                if (validator == null || !isTokenNotExpired(parsed.expEpochSeconds()) || !isSessionValid(validator, parsed.uid(), parsed.sid())) {
                    filterChain.doFilter(request, response);
                    return;
                }
            }

            try {
                provisioningService.ensureExistsBestEffort(parsed.username(), parsed.role(), parsed.uid());
            } catch (Exception ignored) {
            }

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(parsed.username());
            } catch (Exception e) {
                filterChain.doFilter(request, response);
                return;
            }
            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private ParsedTokenClaims parseVerifiedClaims(String token) {
        try {
            Claims claims = jwtService.parseClaims(token);
            return mapClaims(claims.getSubject(), claims.get("uid"), claims.get("sid"), claims.get("role"), claims.get("exp"));
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
                    payload.get("exp")
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private ParsedTokenClaims mapClaims(String username, Object uidRaw, Object sidRaw, Object roleRaw, Object expRaw) {
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

        return new ParsedTokenClaims(username, uid, sid, role, expEpochSeconds);
    }

    private boolean isSessionValid(UserSessionValidator validator, Long uid, String sid) {
        return uid != null && uid > 0 && sid != null && !sid.isBlank() && validator.validate(uid, sid);
    }

    private boolean isTokenNotExpired(Long expEpochSeconds) {
        return expEpochSeconds != null && expEpochSeconds > Instant.now().getEpochSecond();
    }

    private record ParsedTokenClaims(String username, Long uid, String sid, String role, Long expEpochSeconds) {
    }
}
