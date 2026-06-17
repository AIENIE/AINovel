package com.ainovel.app.style.repo;

import com.ainovel.app.style.model.StyleAnalysisJob;
import com.ainovel.app.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StyleAnalysisJobRepository extends JpaRepository<StyleAnalysisJob, UUID> {
    List<StyleAnalysisJob> findByUserOrderByCreatedAtDesc(User user);
}
