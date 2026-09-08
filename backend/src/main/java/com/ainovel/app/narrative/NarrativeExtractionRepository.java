package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
public interface NarrativeExtractionRepository extends JpaRepository<NarrativeExtraction, java.util.UUID> {
    java.util.Optional<NarrativeExtraction> findByApprovalId(java.util.UUID approvalId);
    java.util.List<NarrativeExtraction> findByApprovalLedgerBranchIdOrderByCreatedAtDesc(java.util.UUID branchId);
}
