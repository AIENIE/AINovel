package com.ainovel.app.economy.repo;

import com.ainovel.app.economy.model.ProjectCreditLedger;
import com.ainovel.app.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectCreditLedgerRepository extends JpaRepository<ProjectCreditLedger, UUID> {
    Page<ProjectCreditLedger> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    Page<ProjectCreditLedger> findByOrderByCreatedAtDesc(Pageable pageable);
    List<ProjectCreditLedger> findByUserAndReferenceTypeAndReferenceIdAndEntryType(
            User user,
            String referenceType,
            String referenceId,
            com.ainovel.app.economy.model.CreditLedgerType entryType
    );
    boolean existsByUserAndIdempotencyKey(User user, String idempotencyKey);
    Optional<ProjectCreditLedger> findFirstByUserAndIdempotencyKey(User user, String idempotencyKey);

    @Query("select coalesce(sum(-l.delta), 0) from ProjectCreditLedger l where l.delta < 0")
    long sumNegativeDeltaAbs();

    @Query("select coalesce(sum(-l.delta), 0) from ProjectCreditLedger l where l.delta < 0 and l.createdAt >= :since")
    long sumNegativeDeltaAbsSince(@Param("since") Instant since);
}
