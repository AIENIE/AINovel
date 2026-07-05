package com.ainovel.app.common;

import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserResolver {
    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require(UserDetails details) {
        if (details == null) {
            throw new BusinessException("未登录");
        }
        return userRepository.findByUsername(details.getUsername())
                .orElseThrow(() -> new BusinessException("用户不存在"));
    }
}
