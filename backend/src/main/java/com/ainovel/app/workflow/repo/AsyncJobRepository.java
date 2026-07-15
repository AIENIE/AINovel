package com.ainovel.app.workflow.repo;

import com.ainovel.app.workflow.model.AsyncJob;
import com.ainovel.app.workflow.model.AsyncJobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AsyncJobRepository extends JpaRepository<AsyncJob, UUID> {
    List<AsyncJob> findByScopeIdOrderByCreatedAtDesc(UUID scopeId);
    List<AsyncJob> findByStatusIn(Collection<AsyncJobStatus> statuses);
    Optional<AsyncJob> findByIdempotencyKey(String idempotencyKey);

    @Query("select job from AsyncJob job join fetch job.user where job.id = :id")
    Optional<AsyncJob> findWithUserById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select job from AsyncJob job join fetch job.user where job.id = :id")
    Optional<AsyncJob> findByIdForUpdate(@Param("id") UUID id);
}
