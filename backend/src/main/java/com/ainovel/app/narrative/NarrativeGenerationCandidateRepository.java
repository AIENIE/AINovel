package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NarrativeGenerationCandidateRepository extends JpaRepository<NarrativeGenerationCandidate,UUID> {
    List<NarrativeGenerationCandidate> findTop20ByManuscriptIdAndBranchIdOrderByCreatedAtDesc(UUID manuscriptId,UUID branchId);
}
