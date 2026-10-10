package com.ainovel.app.manuscript.repo;

import com.ainovel.app.manuscript.model.ManuscriptCreationReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ManuscriptCreationReceiptRepository extends JpaRepository<ManuscriptCreationReceipt, UUID> {
    Optional<ManuscriptCreationReceipt> findByUserIdAndOutlineIdAndRequestKey(UUID userId, UUID outlineId, String requestKey);
}
