package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2BetaReaderReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2BetaReaderReportRepository extends JpaRepository<V2BetaReaderReport, UUID> {
    List<V2BetaReaderReport> findByStoryIdOrderByCreatedAtDesc(UUID storyId);
    Optional<V2BetaReaderReport> findByStoryIdAndId(UUID storyId, UUID id);
}
