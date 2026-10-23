package com.ainovel.app.world.repo;
import com.ainovel.app.world.model.WorldPublishReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WorldPublishReceiptRepository extends JpaRepository<WorldPublishReceipt,UUID> {
    Optional<WorldPublishReceipt> findByWorldIdAndRequestKey(UUID worldId,String requestKey);
}
