package com.ainovel.app.adminauth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.PathContainer;

import java.util.List;

final class AdminRequestPaths {
    private static final String INVALID_CANONICAL_PATH = "/v1/admin/_invalid-canonical-path";

    private AdminRequestPaths() {
    }

    static String normalized(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        String context = request == null ? "" : request.getContextPath();
        if (context != null && !context.isBlank() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        path = canonicalPath(path);
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }
        return path;
    }

    private static String canonicalPath(String rawPath) {
        try {
            List<PathContainer.Element> elements = PathContainer.parsePath(rawPath).elements();
            StringBuilder canonical = new StringBuilder(rawPath.length());
            for (PathContainer.Element element : elements) {
                canonical.append(element instanceof PathContainer.PathSegment segment
                        ? segment.valueToMatch()
                        : element.value());
            }
            return canonical.toString();
        } catch (IllegalArgumentException ex) {
            // Ambiguous or malformed paths are classified as administrator paths and fail closed.
            return INVALID_CANONICAL_PATH;
        }
    }

    static boolean isAdminAuth(String path) {
        return "/v1/admin-auth".equals(path) || path.startsWith("/v1/admin-auth/");
    }

    static boolean isAdminBusiness(String path) {
        return "/v1/admin".equals(path) || path.startsWith("/v1/admin/")
                || "/v2/admin".equals(path) || path.startsWith("/v2/admin/");
    }

    static boolean isUnsafeMethod(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
