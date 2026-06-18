package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2WritingSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2WritingSessionRepository extends JpaRepository<V2WritingSession, UUID> {
    List<V2WritingSession> findByUserId(UUID userId);
    Optional<V2WritingSession> findByUserIdAndId(UUID userId, UUID id);
}
