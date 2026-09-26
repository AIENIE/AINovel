package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NarrativeContextRevisionRepository extends JpaRepository<NarrativeContextRevision, UUID> {
    Optional<NarrativeContextRevision> findFirstByBranchIdOrderByRevisionDesc(UUID branchId);
    Optional<NarrativeContextRevision> findByBranchIdAndIdempotencyKey(UUID branchId, String key);
    List<NarrativeContextRevision> findByBranchIdOrderByRevisionDesc(UUID branchId);
}
