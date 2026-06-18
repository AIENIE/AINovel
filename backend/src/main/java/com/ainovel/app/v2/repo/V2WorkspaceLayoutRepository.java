package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2WorkspaceLayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2WorkspaceLayoutRepository extends JpaRepository<V2WorkspaceLayout, UUID> {
    List<V2WorkspaceLayout> findByUserId(UUID userId);
    Optional<V2WorkspaceLayout> findByUserIdAndId(UUID userId, UUID id);
}
