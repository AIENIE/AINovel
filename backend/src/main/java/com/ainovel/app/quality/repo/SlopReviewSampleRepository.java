package com.ainovel.app.quality.repo;

import com.ainovel.app.quality.SlopReviewSampleStatus;
import com.ainovel.app.quality.model.SlopReviewSample;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SlopReviewSampleRepository extends JpaRepository<SlopReviewSample, UUID> {
    Optional<SlopReviewSample> findBySourceTypeAndSourceRunId(String sourceType, UUID sourceRunId);

    boolean existsBySampleId(String sampleId);

    List<SlopReviewSample> findTop200ByOrderByCreatedAtDesc();

    List<SlopReviewSample> findTop200ByStatusOrderByCreatedAtDesc(SlopReviewSampleStatus status);
}
