package com.ainovel.app.aioperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiOperationRepository extends JpaRepository<AiOperationRun, UUID> {
    Optional<AiOperationRun> findByIdAndUserId(UUID id, UUID userId);
    Optional<AiOperationRun> findFirstByUserIdAndScopeTypeAndScopeIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, String scopeType, UUID scopeId, Collection<AiOperationStatus> statuses);
    List<AiOperationRun> findByStatusIn(Collection<AiOperationStatus> statuses);
}
