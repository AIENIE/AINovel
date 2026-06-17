package com.ainovel.app.quality.repo;

import com.ainovel.app.quality.model.SlopDriftRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlopDriftRunRepository extends JpaRepository<SlopDriftRun, UUID> {
    List<SlopDriftRun> findTop20ByManuscriptIdOrderByCreatedAtDesc(UUID manuscriptId);
}
