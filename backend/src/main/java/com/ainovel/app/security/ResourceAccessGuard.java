package com.ainovel.app.security;

import com.ainovel.app.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class ResourceAccessGuard {

    public String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new AccessDeniedException("未认证用户禁止访问");
        }
        return auth.getName();
    }

    public boolean isCurrentUserAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    public void assertCurrentUserEquals(String username) {
        if (isCurrentUserAdmin()) {
            return;
        }
        String current = currentUsername();
        if (!current.equals(username)) {
            throw new AccessDeniedException("无权访问其他用户资源");
        }
    }

    public void assertOwner(User owner) {
        if (isCurrentUserAdmin()) {
            return;
        }
        String ownerUsername = owner == null ? null : owner.getUsername();
        if (ownerUsername == null || ownerUsername.isBlank()) {
            throw new AccessDeniedException("资源归属信息缺失，拒绝访问");
        }
        String current = currentUsername();
        if (!current.equals(ownerUsername)) {
            throw new AccessDeniedException("无权访问其他用户资源");
        }
    }

    public void assertAdmin() {
        if (!isCurrentUserAdmin()) {
            throw new AccessDeniedException("仅管理员允许访问该资源");
        }
    }
}
