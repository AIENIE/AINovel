package com.ainovel.app.material.repo;

import com.ainovel.app.material.model.MaterialUploadJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MaterialUploadJobRepository extends JpaRepository<MaterialUploadJob, UUID> {
    java.util.Optional<MaterialUploadJob> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);}
