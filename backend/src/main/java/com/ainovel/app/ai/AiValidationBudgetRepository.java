package com.ainovel.app.ai;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;
public interface AiValidationBudgetRepository extends JpaRepository<AiValidationBudget,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from AiValidationBudget b where b.id=:id")
    Optional<AiValidationBudget> lock(@Param("id") String id);
}
