package com.ainovel.app.quality.repo;

import com.ainovel.app.quality.model.SlopQualityIssue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SlopQualityIssueRepository extends JpaRepository<SlopQualityIssue, UUID> {
}
