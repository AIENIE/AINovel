package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NarrativeRecordRepository extends JpaRepository<NarrativeRecord, java.util.UUID> {
    java.util.List<NarrativeRecord> findByExtractionApprovalLedgerBranchIdOrderByCreatedAtAsc(java.util.UUID branchId);
}
