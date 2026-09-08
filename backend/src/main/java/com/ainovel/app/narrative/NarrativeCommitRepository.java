package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NarrativeCommitRepository extends JpaRepository<NarrativeCommit, java.util.UUID> {
    java.util.Optional<NarrativeCommit> findByLedgerBranchIdAndIdempotencyKey(java.util.UUID branchId, String key);
}
