package com.ainovel.app.adminauth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequestWrapper;

@Component
public class AdminBusinessAccessFilter extends OncePerRequestFilter {
    private static final int MAX_PROOF_BOUND_BODY_BYTES = 1024 * 1024;
    private final AdminAuthPolicySource policy;
    private final AdminOperationProofService proofs;
    private final ObjectMapper objectMapper;

    public AdminBusinessAccessFilter(
            AdminAuthPolicySource policy,
            AdminOperationProofService proofs,
            ObjectMapper objectMapper
    ) {
        this.policy = policy;
        this.proofs = proofs;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !AdminRequestPaths.isAdminBusiness(AdminRequestPaths.normalized(request));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!hasLocalAdminAuthority(authentication)) {
            write(response, HttpServletResponse.SC_FORBIDDEN, Map.of(
                    "code", "LOCAL_ADMIN_REQUIRED",
                    "message", "该后台仅允许本地管理员会话访问"
            ));
            return;
        }
        if (!policy.requiresTotp() || !isHighRisk(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        RepeatableBodyRequest proofBoundRequest;
        try {
            proofBoundRequest = new RepeatableBodyRequest(request, MAX_PROOF_BOUND_BODY_BYTES);
        } catch (RequestBodyTooLargeException ex) {
            write(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, Map.of(
                    "code", "ADMIN_OPERATION_BODY_TOO_LARGE",
                    "message", "高风险操作请求体过大"
            ));
            return;
        }
        String sessionHash = authentication.getDetails() instanceof String value ? value : "";
        String path = AdminRequestPaths.normalized(proofBoundRequest);
        String actionKey = proofBoundRequest.getMethod() + ":" + path;
        String targetId = targetFingerprint(proofBoundRequest, path);
        String proofToken = request.getHeader(AdminAuthConstants.OPERATION_PROOF_HEADER);
        if (proofs.consume(
                proofToken,
                AdminLocalAuthService.SUBJECT,
                sessionHash,
                actionKey,
                targetId
        )) {
            filterChain.doFilter(proofBoundRequest, response);
            return;
        }

        try {
            AdminOperationProofService.OperationChallenge challenge = proofs.createChallenge(
                    AdminLocalAuthService.SUBJECT,
                    sessionHash,
                    actionKey,
                    targetId,
                    source(request)
            );
            write(response, 428, Map.of(
                    "code", "OPERATION_PROOF_REQUIRED",
                    "message", "该操作需要动态验证码二次确认",
                    "challengeId", challenge.challengeId(),
                    "expiresInSeconds", AdminOperationProofService.CHALLENGE_TTL_SECONDS
            ));
        } catch (AdminAuthenticationException ex) {
            if (ex.retryAfterSeconds() > 0) {
                response.setHeader("Retry-After", Integer.toString(ex.retryAfterSeconds()));
            }
            write(response, ex.status().value(), Map.of("code", ex.code(), "message", ex.getMessage()));
        }
    }

    private boolean hasLocalAdminAuthority(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> AdminAuthConstants.FULL_AUTHORITY.equals(authority.getAuthority()));
    }

    private boolean isHighRisk(HttpServletRequest request) {
        String method = request.getMethod();
        String path = AdminRequestPaths.normalized(request);
        if ("DELETE".equals(method)) {
            return true;
        }
        if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
            return false;
        }
        return path.equals("/v1/admin/system-config")
                || path.equals("/v1/admin/credits/grant")
                || path.equals("/v1/admin/redeem-codes")
                || path.equals("/v1/admin/materials/merge")
                || path.matches("/v1/admin/g2-evaluations/[^/]+/status");
    }

    private String source(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private String targetFingerprint(RepeatableBodyRequest request, String path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, path);
            update(digest, request.getQueryString() == null ? "" : request.getQueryString());
            digest.update(request.body());
            return path + "#" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private void write(HttpServletResponse response, int status, Map<String, ?> body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(response.getWriter(), body);
    }

    private static final class RepeatableBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private RepeatableBodyRequest(HttpServletRequest request, int maxBytes) throws IOException {
            super(request);
            body = request.getInputStream().readNBytes(maxBytes + 1);
            if (body.length > maxBytes) {
                throw new RequestBodyTooLargeException();
            }
        }

        private byte[] body() {
            return body;
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) {
                    if (listener == null) return;
                    try {
                        if (isFinished()) listener.onAllDataRead(); else listener.onDataAvailable();
                    } catch (IOException ex) {
                        listener.onError(ex);
                    }
                }
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(
                    getInputStream(), encoding == null ? StandardCharsets.UTF_8 : java.nio.charset.Charset.forName(encoding)
            ));
        }
    }

    private static final class RequestBodyTooLargeException extends IOException {
    }
}
