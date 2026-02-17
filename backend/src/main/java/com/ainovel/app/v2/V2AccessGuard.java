package com.ainovel.app.v2;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.story.repo.StoryRepository;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class V2AccessGuard {
    private final UserRepository userRepository;
    private final StoryRepository storyRepository;
    private final ManuscriptRepository manuscriptRepository;

    public V2AccessGuard(UserRepository userRepository,
                         StoryRepository storyRepository,
                         ManuscriptRepository manuscriptRepository) {
        this.userRepository = userRepository;
        this.storyRepository = storyRepository;
        this.manuscriptRepository = manuscriptRepository;
    }

    public User currentUser(UserDetails details) {
        if (details == null) {
            throw new RuntimeException("未登录");
        }
        return userRepository.findByUsername(details.getUsername()).orElseThrow(() -> new RuntimeException("用户不存在"));
    }

    public Story requireOwnedStory(UUID storyId, User user) {
        Story story = storyRepository.findById(storyId).orElseThrow(() -> new RuntimeException("故事不存在"));
        if (story.getUser() == null || !story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权访问该故事");
        }
        return story;
    }

    public Manuscript requireOwnedManuscript(UUID manuscriptId, User user) {
        Manuscript manuscript = manuscriptRepository.findWithStoryById(manuscriptId)
                .orElseThrow(() -> new RuntimeException("稿件不存在"));
        if (manuscript.getOutline() == null
                || manuscript.getOutline().getStory() == null
                || manuscript.getOutline().getStory().getUser() == null
                || !manuscript.getOutline().getStory().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("无权访问该稿件");
        }
        return manuscript;
    }

    public void requireAdmin(User user) {
        if (!user.hasRole("ROLE_ADMIN") && !user.hasRole("ADMIN")) {
            throw new RuntimeException("无管理员权限");
        }
    }
}
