package com.ainovel.app.adminauth;

import com.ainovel.app.security.JwtService;
import com.ainovel.app.user.User;
import com.ainovel.app.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AdminLocalAuthService {
    private final AdminLocalAuthProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AdminLocalAuthService(
            AdminLocalAuthProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResult login(String username, String password) {
        String expectedUsername = normalize(properties.getUsername());
        String expectedPassword = properties.getPassword() == null ? "" : properties.getPassword();
        ensureConfigured(expectedUsername, expectedPassword);

        if (!secureEquals(normalize(username), expectedUsername) || !secureEquals(password == null ? "" : password, expectedPassword)) {
            throw new RuntimeException("用户名或密码错误");
        }

        User adminUser = ensureAdminUser(expectedUsername, expectedPassword);
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "ADMIN");
        claims.put("local_admin", true);
        claims.put("sid", "local-admin-" + UUID.randomUUID());
        claims.put("uid", 0L);
        String token = jwtService.generateToken(adminUser.getUsername(), claims);
        return new LoginResult(token, adminUser.getUsername(), Instant.now());
    }

    public MeResult me(String username) {
        return new MeResult(username);
    }

    private User ensureAdminUser(String username, String plainPassword) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setEmail("local_admin@" + username + ".local");
            user.setPasswordHash(passwordEncoder.encode(plainPassword));
            user.setCredits(0.0);
            user.setBanned(false);
            Set<String> roles = new HashSet<>();
            roles.add("ROLE_USER");
            roles.add("ROLE_ADMIN");
            user.setRoles(roles);
            return userRepository.save(user);
        }

        boolean changed = false;
        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank() || !passwordEncoder.matches(plainPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(plainPassword));
            changed = true;
        }

        Set<String> roles = user.getRoles() == null ? new HashSet<>() : new HashSet<>(user.getRoles());
        if (!roles.contains("ROLE_ADMIN") || !roles.contains("ROLE_USER")) {
            roles.add("ROLE_USER");
            roles.add("ROLE_ADMIN");
            user.setRoles(roles);
            changed = true;
        }

        if (user.isBanned()) {
            user.setBanned(false);
            changed = true;
        }

        return changed ? userRepository.save(user) : user;
    }

    private void ensureConfigured(String username, String password) {
        if (username.isBlank()) {
            throw new IllegalStateException("ADMIN_USERNAME 未配置");
        }
        if (password.isBlank()) {
            throw new IllegalStateException("ADMIN_PASSWORD 未配置");
        }
        String upper = password.trim().toUpperCase();
        if (upper.startsWith("REPLACE_ME")
                || upper.contains("CHANGE-ME")
                || upper.contains("CHANGE_ME")
                || upper.contains("REPLACE_WITH")) {
            throw new IllegalStateException("ADMIN_PASSWORD 不能是占位值");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean secureEquals(String left, String right) {
        byte[] a = (left == null ? "" : left).getBytes(StandardCharsets.UTF_8);
        byte[] b = (right == null ? "" : right).getBytes(StandardCharsets.UTF_8);
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }

    public record LoginResult(String token, String username, Instant loggedInAt) {
    }

    public record MeResult(String username) {
    }
}

