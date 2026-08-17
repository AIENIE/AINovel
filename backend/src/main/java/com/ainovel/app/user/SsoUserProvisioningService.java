package com.ainovel.app.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class SsoUserProvisioningService {
    private static final Logger log = LoggerFactory.getLogger(SsoUserProvisioningService.class);
    private static final String USER_SERVICE_ISSUER = "aienie-user-service";

    private final UserRepository userRepository;
    private final ExternalIdentityRepository identityRepository;

    public SsoUserProvisioningService(UserRepository userRepository, ExternalIdentityRepository identityRepository) {
        this.userRepository = userRepository;
        this.identityRepository = identityRepository;
    }

    @Transactional
    public User ensureExistsBestEffort(String usernameRaw, String roleRaw, Long remoteUid) {
        String username = usernameRaw != null ? usernameRaw.trim() : "";
        if (username.isBlank()) {
            return null;
        }

        if (remoteUid == null || remoteUid <= 0) {
            throw identityConflict("missing_remote_uid", username, remoteUid);
        }

        ExternalIdentity identity = identityRepository
                .findByIssuerAndRemoteUid(USER_SERVICE_ISSUER, remoteUid)
                .orElse(null);
        User user = identity == null ? null : identity.getUser();

        Set<String> roles = resolveRoles(roleRaw);

        if (user == null) {
            User usernameOwner = userRepository.findByUsername(username).orElse(null);
            if (usernameOwner != null) {
                throw identityConflict("username_owned_by_another_identity", username, remoteUid);
            }
            user = new User();
            user.setUsername(username);
            user.setEmail(allocateSsoEmail(username));
            user.setPasswordHash(allocateSsoPasswordPlaceholder());
            user.setRoles(roles);
            user.setBanned(false);
            user.setRemoteUid(remoteUid);
            user = userRepository.save(user);
            ExternalIdentity createdIdentity = new ExternalIdentity();
            createdIdentity.setUser(user);
            createdIdentity.setIssuer(USER_SERVICE_ISSUER);
            createdIdentity.setRemoteUid(remoteUid);
            createdIdentity.setVerifiedUsername(username);
            identityRepository.save(createdIdentity);
            return user;
        }

        boolean changed = false;
        if (user.getRemoteUid() != null && !user.getRemoteUid().equals(remoteUid)) {
            throw identityConflict("immutable_uid_mismatch", username, remoteUid);
        }
        if (user.getRemoteUid() == null) {
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
            } else {
                throw identityConflict("username_conflict_on_rename", username, remoteUid);
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
            userRepository.save(user);
        }
        if (!username.equals(identity.getVerifiedUsername())) {
            identity.setVerifiedUsername(username);
            identityRepository.save(identity);
        }
        return user;
    }

    private IllegalStateException identityConflict(String reason, String username, Long remoteUid) {
        log.warn("event=sso_identity_conflict reason={} username={} remoteUid={}", reason, username, remoteUid);
        return new IllegalStateException("SSO_IDENTITY_CONFLICT");
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

}
