package com.ainovel.app.user;

import com.ainovel.app.economy.model.ProjectCreditAccount;
import com.ainovel.app.economy.repo.ProjectCreditAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class SsoUserProvisioningService {

    private final UserRepository userRepository;
    private final ProjectCreditAccountRepository accountRepository;

    public SsoUserProvisioningService(UserRepository userRepository, ProjectCreditAccountRepository accountRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void ensureExistsBestEffort(String usernameRaw, String roleRaw, Long remoteUid) {
        String username = usernameRaw != null ? usernameRaw.trim() : "";
        if (username.isBlank()) {
            return;
        }

        User user = null;
        if (remoteUid != null && remoteUid > 0) {
            user = userRepository.findByRemoteUid(remoteUid).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByUsername(username).orElse(null);
        }

        Set<String> roles = resolveRoles(roleRaw);

        if (user == null) {
            user = new User();
            user.setUsername(username);
            user.setEmail(allocateSsoEmail(username));
            user.setPasswordHash(allocateSsoPasswordPlaceholder());
            user.setRoles(roles);
            user.setBanned(false);
            if (remoteUid != null && remoteUid > 0) {
                user.setRemoteUid(remoteUid);
            }
            user = userRepository.save(user);
            ensureProjectAccount(user);
            return;
        }

        boolean changed = false;

        if (remoteUid != null && remoteUid > 0 && (user.getRemoteUid() == null || !user.getRemoteUid().equals(remoteUid))) {
            user.setRemoteUid(remoteUid);
            changed = true;
        }

        if (!username.equals(user.getUsername())) {
            // Best-effort: avoid crashing on unique constraint; keep old username if taken by others.
            var currentId = user.getId();
            boolean taken = userRepository.findByUsername(username).filter(u -> !u.getId().equals(currentId)).isPresent();
            if (!taken) {
                user.setUsername(username);
                changed = true;
            }
        }

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            user.setEmail(allocateSsoEmail(username));
            changed = true;
        }

        if (user.getPasswordHash() == null || user.getPasswordHash().isBlank()) {
            user.setPasswordHash(allocateSsoPasswordPlaceholder());
            changed = true;
        }

        if (user.getRoles() == null || !user.getRoles().equals(roles)) {
            user.setRoles(roles);
            changed = true;
        }

        if (changed) {
            user = userRepository.save(user);
        }
        ensureProjectAccount(user);
    }

    private String allocateSsoEmail(String username) {
        String base = username.trim().toLowerCase(Locale.ROOT);
        base = base.replaceAll("[^a-z0-9._+-]", "_");
        if (base.isBlank()) base = "user";
        if (base.length() > 48) base = base.substring(0, 48);

        String candidate = "sso_" + base + "@sso.local";
        if (userRepository.findByEmail(candidate).isPresent()) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            candidate = "sso_" + base + "_" + suffix + "@sso.local";
        }
        return candidate;
    }

    private String allocateSsoPasswordPlaceholder() {
        return "SSO:" + UUID.randomUUID();
    }

    private Set<String> resolveRoles(String roleRaw) {
        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        if ("ADMIN".equalsIgnoreCase(roleRaw)) {
            roles.add("ROLE_ADMIN");
        }
        return roles;
    }

    private void ensureProjectAccount(User user) {
        accountRepository.findByUser(user).orElseGet(() -> {
            ProjectCreditAccount account = new ProjectCreditAccount();
            account.setUser(user);
            account.setBalance(Math.max(0L, Math.round(user.getCredits())));
            return accountRepository.save(account);
        });
    }
}
