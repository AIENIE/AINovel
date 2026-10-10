package com.ainovel.app.quality.language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LanguageDecisionRepository extends JpaRepository<LanguageDecision,UUID> {
    Optional<LanguageDecision> findByPatchIdAndRequestKey(UUID patchId,String requestKey);
}
