package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2KeyboardShortcut;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2KeyboardShortcutRepository extends JpaRepository<V2KeyboardShortcut, UUID> {
    List<V2KeyboardShortcut> findByUserId(UUID userId);
    Optional<V2KeyboardShortcut> findByUserIdAndAction(UUID userId, String action);
}
