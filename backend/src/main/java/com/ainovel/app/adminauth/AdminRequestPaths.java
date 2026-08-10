package com.ainovel.app.adminauth;

import jakarta.servlet.http.HttpServletRequest;

final class AdminRequestPaths {
    private AdminRequestPaths() {
    }

    static String normalized(HttpServletRequest request) {
        String path = request == null ? "" : request.getRequestURI();
        String context = request == null ? "" : request.getContextPath();
        if (context != null && !context.isBlank() && path.startsWith(context)) {
            path = path.substring(context.length());
        }
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }
        return path;
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
