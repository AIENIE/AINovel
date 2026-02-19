package com.ainovel.app.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;

@RestController
@RequestMapping("/v1/sso")
public class SsoController {

    private static final String DEFAULT_NEXT_PATH = "/workbench";
    private final SsoEntryService ssoEntryService;

    public SsoController(SsoEntryService ssoEntryService) {
        this.ssoEntryService = ssoEntryService;
    }

    @Operation(summary = "跳转统一登录页", description = "由后端生成 userservice 登录入口并 302 跳转。")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "重定向到 userservice 登录页"),
            @ApiResponse(responseCode = "400", description = "state 缺失或非法"),
            @ApiResponse(responseCode = "502", description = "userservice 地址解析失败")
    })
    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @Parameter(description = "登录成功后前端内部回跳路径", example = "/workbench")
            @RequestParam(value = "next", required = false) String next,
            @Parameter(description = "一次性随机 state", required = true)
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request
    ) {
        return redirect("login", next, state, request);
    }

    @Operation(summary = "跳转统一注册页", description = "由后端生成 userservice 注册入口并 302 跳转。")
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "重定向到 userservice 注册页"),
            @ApiResponse(responseCode = "400", description = "state 缺失或非法"),
            @ApiResponse(responseCode = "502", description = "userservice 地址解析失败")
    })
    @GetMapping("/register")
    public ResponseEntity<Void> register(
            @Parameter(description = "注册成功后前端内部回跳路径", example = "/workbench")
            @RequestParam(value = "next", required = false) String next,
            @Parameter(description = "一次性随机 state", required = true)
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request
    ) {
        return redirect("register", next, state, request);
    }

    private ResponseEntity<Void> redirect(String mode, String next, String state, HttpServletRequest request) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "STATE_REQUIRED");
        }
        String safeNext = normalizeNextPath(next);
        String callback = buildCallbackUrl(request, safeNext);
        try {
            URI target = "register".equals(mode)
                    ? ssoEntryService.buildRegisterRedirectUri(callback, state)
                    : ssoEntryService.buildLoginRedirectUri(callback, state);
            return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "USER_SERVICE_UNAVAILABLE", ex);
        }
    }

    private String normalizeNextPath(String next) {
        if (next == null || next.isBlank()) {
            return DEFAULT_NEXT_PATH;
        }
        String candidate = next.trim();
        if (!candidate.startsWith("/") || candidate.startsWith("//")) {
            return DEFAULT_NEXT_PATH;
        }
        return candidate;
    }

    private String buildCallbackUrl(HttpServletRequest request, String next) {
        String origin = resolveOrigin(request);
        return UriComponentsBuilder.fromHttpUrl(origin)
                .path("/sso/callback")
                .queryParam("next", next)
                .build()
                .toUriString();
    }

    private String resolveOrigin(HttpServletRequest request) {
        String scheme = firstHeaderValue(request, "X-Forwarded-Proto");
        if (scheme == null || scheme.isBlank()) {
            scheme = request.getScheme();
        }

        String host = firstHeaderValue(request, "X-Forwarded-Host");
        if (host == null || host.isBlank()) {
            String origin = parseOriginHeader(firstHeaderValue(request, "Origin"));
            if (origin != null) {
                return origin;
            }
        }
        if (host == null || host.isBlank()) {
            String refererOrigin = parseOriginHeader(firstHeaderValue(request, "Referer"));
            if (refererOrigin != null) {
                return refererOrigin;
            }
        }
        if (host == null || host.isBlank()) {
            host = firstHeaderValue(request, "Host");
        }
        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            boolean appendPort = port > 0
                    && !("http".equalsIgnoreCase(scheme) && port == 80)
                    && !("https".equalsIgnoreCase(scheme) && port == 443);
            if (appendPort) {
                host += ":" + port;
            }
        }

        return scheme + "://" + host;
    }

    private String parseOriginHeader(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(raw);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            StringBuilder sb = new StringBuilder(uri.getScheme()).append("://").append(uri.getHost());
            int port = uri.getPort();
            if (port > 0) {
                boolean defaultPort = ("http".equalsIgnoreCase(uri.getScheme()) && port == 80)
                        || ("https".equalsIgnoreCase(uri.getScheme()) && port == 443);
                if (!defaultPort) {
                    sb.append(":").append(port);
                }
            }
            return sb.toString();
        } catch (URISyntaxException ignored) {
            return null;
        }
    }

    private String firstHeaderValue(HttpServletRequest request, String name) {
        String raw = request.getHeader(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int commaIdx = raw.indexOf(',');
        if (commaIdx >= 0) {
            raw = raw.substring(0, commaIdx);
        }
        String value = raw.trim();
        return value.isEmpty() ? null : value;
    }
}
