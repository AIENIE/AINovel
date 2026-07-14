package com.ainovel.app.quality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlopPatternRepository extends JpaRepository<SlopPattern, UUID> {
    List<SlopPattern> findByEnabledTrue();
    List<SlopPattern> findByCategoryAndEnabledTrue(String category);
}
