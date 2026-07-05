package com.ainovel.app.security;

import com.ainovel.app.common.BusinessException;
import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ResourceAccessGuard {
    private final CurrentUserResolver currentUserResolver;
    private final StoryRepository storyRepository;
    private final ManuscriptRepository manuscriptRepository;

    public ResourceAccessGuard(CurrentUserResolver currentUserResolver,
                               StoryRepository storyRepository,
                               ManuscriptRepository manuscriptRepository) {
        this.currentUserResolver = currentUserResolver;
        this.storyRepository = storyRepository;
        this.manuscriptRepository = manuscriptRepository;
    }

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

    public User currentUser(UserDetails details) {
        return currentUserResolver.require(details);
    }

    public Story requireOwnedStory(UUID storyId, User user) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new BusinessException("故事不存在"));
        if (story.getUser() == null || !story.getUser().getId().equals(user.getId())) {
            throw new BusinessException("无权访问该故事");
        }
        return story;
    }

    public Manuscript requireOwnedManuscript(UUID manuscriptId, User user) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new BusinessException("稿件不存在"));
        if (manuscript.getOutline() == null
                || manuscript.getOutline().getStory() == null
                || manuscript.getOutline().getStory().getUser() == null
                || !manuscript.getOutline().getStory().getUser().getId().equals(user.getId())) {
            throw new BusinessException("无权访问该稿件");
        }
        return manuscript;
    }

    public void requireAdmin(User user) {
        if (!user.hasRole("ROLE_ADMIN") && !user.hasRole("ADMIN")) {
            throw new BusinessException("无管理员权限");
        }
    }
}
