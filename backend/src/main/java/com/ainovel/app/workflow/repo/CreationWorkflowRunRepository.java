package com.ainovel.app.workflow.repo;

import com.ainovel.app.user.User;
import com.ainovel.app.workflow.model.CreationWorkflowRun;
import com.ainovel.app.workflow.model.CreationWorkflowStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CreationWorkflowRunRepository extends JpaRepository<CreationWorkflowRun, UUID> {
    List<CreationWorkflowRun> findByUserOrderByUpdatedAtDesc(User user);

    Optional<CreationWorkflowRun> findByIdAndUser(UUID id, User user);

    List<CreationWorkflowRun> findByAutoRunTrueAndStatusIn(Collection<CreationWorkflowStatus> statuses);

    @Query("select run from CreationWorkflowRun run join fetch run.user where run.id = :id")
    Optional<CreationWorkflowRun> findWithUserById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select run from CreationWorkflowRun run join fetch run.user where run.id = :id")
    Optional<CreationWorkflowRun> findByIdForUpdate(@Param("id") UUID id);
}
