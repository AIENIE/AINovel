package com.ainovel.app.v2.repo;

import com.ainovel.app.v2.model.V2ExportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface V2ExportTemplateRepository extends JpaRepository<V2ExportTemplate, UUID> {
    List<V2ExportTemplate> findByUserIdOrUserIsNull(UUID userId);
}
