package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2WritingGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2WritingGoalRepository extends JpaRepository<V2WritingGoal, UUID> {
    List<V2WritingGoal> findByUserId(UUID userId);
    Optional<V2WritingGoal> findByUserIdAndId(UUID userId, UUID id);
}
