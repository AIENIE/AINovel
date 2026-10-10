package com.ainovel.app.quality.language;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface LanguageReportRepository extends JpaRepository<LanguageReport,UUID> {
    Optional<LanguageReport> findByManuscriptIdAndSceneIdAndSourceKey(UUID manuscriptId,UUID sceneId,String sourceKey);
    List<LanguageReport> findTop20ByManuscriptIdAndSceneIdOrderByCreatedAtDesc(UUID manuscriptId,UUID sceneId);
}
