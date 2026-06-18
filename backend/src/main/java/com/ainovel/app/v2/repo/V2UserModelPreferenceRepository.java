package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2UserModelPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface V2UserModelPreferenceRepository extends JpaRepository<V2UserModelPreference, UUID> {
    List<V2UserModelPreference> findByUserId(UUID userId);
    Optional<V2UserModelPreference> findByUserIdAndTaskType(UUID userId, String taskType);
    void deleteByUserIdAndTaskType(UUID userId, String taskType);
}
