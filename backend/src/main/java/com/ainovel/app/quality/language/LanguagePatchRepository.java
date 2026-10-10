package com.ainovel.app.quality.language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LanguagePatchRepository extends JpaRepository<LanguagePatch,UUID> {
    Optional<LanguagePatch> findByReportIdAndIssueId(UUID reportId,String issueId);
    List<LanguagePatch> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
