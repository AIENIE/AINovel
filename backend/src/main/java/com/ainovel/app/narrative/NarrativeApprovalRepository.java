package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NarrativeApprovalRepository extends JpaRepository<NarrativeApproval, java.util.UUID> {
    java.util.Optional<NarrativeApproval> findByLedgerBranchIdAndIdempotencyKey(java.util.UUID branchId, String key);
}
