package com.ainovel.app.security;

import com.ainovel.app.common.SafeLogThrowable;
import com.ainovel.app.security.remote.UserSessionValidator;
import com.ainovel.app.user.SsoUserProvisioningService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final SsoUserProvisioningService provisioningService;
    private final ObjectProvider<UserSessionValidator> userSessionValidatorProvider;

    public JwtAuthFilter(
            JwtService jwtService,
            org.springframework.security.core.userdetails.UserDetailsService userDetailsService,
            SsoUserProvisioningService provisioningService,
            ObjectProvider<UserSessionValidator> userSessionValidatorProvider
    ) {
        this.jwtService = jwtService;
        this.provisioningService = provisioningService;
        this.userSessionValidatorProvider = userSessionValidatorProvider;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String token = bearer(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.parseClaims(token);
            if (Boolean.TRUE.equals(claims.get("local_admin"))) {
                filterChain.doFilter(request, response);
                return;
            }
            String username = claims.getSubject();
            Long uid = longClaim(claims.get("uid"));
            String sid = stringClaim(claims.get("sid"));
            String role = stringClaim(claims.get("role"));
            if (username == null || username.isBlank() || uid == null || uid <= 0 || sid == null || sid.isBlank()) {
                filterChain.doFilter(request, response);
                return;
            }

            UserSessionValidator validator = userSessionValidatorProvider.getIfAvailable();
            if (validator != null && !validator.validate(uid, sid)) {
                filterChain.doFilter(request, response);
                return;
            }

            com.ainovel.app.user.User localUser = provisioningService.ensureExistsBestEffort(username, role, uid);
            if (localUser == null) {
                filterChain.doFilter(request, response);
                return;
            }
            UserDetails userDetails = new AuthenticatedUserPrincipal(localUser);
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception ex) {
            log.warn("Signed user token validation failed errorType={}",
                    ex.getClass().getSimpleName(), SafeLogThrowable.stackOnly(ex));
        }
        filterChain.doFilter(request, response);
    }

    private String bearer(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        return token.isBlank() ? null : token;
    }

    private Long longClaim(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String stringClaim(Object value) {
        return value instanceof String text ? text : null;
    }
}
