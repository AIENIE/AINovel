package com.ainovel.app.aioperation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AiOperationRepository extends JpaRepository<AiOperationRun, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select r from AiOperationRun r where r.id = :id")
    Optional<AiOperationRun> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<AiOperationRun> findByIdAndUserId(UUID id, UUID userId);
    Optional<AiOperationRun> findFirstByUserIdAndScopeTypeAndScopeIdAndStatusInOrderByCreatedAtDesc(
            UUID userId, String scopeType, UUID scopeId, Collection<AiOperationStatus> statuses);
    List<AiOperationRun> findByStatusIn(Collection<AiOperationStatus> statuses);
    Optional<AiOperationRun> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
    Optional<AiOperationRun> findByActiveScopeKey(String activeScopeKey);
    List<AiOperationRun> findTop100ByStatusOrderByCreatedAtAsc(AiOperationStatus status);
}
