package com.ainovel.app.ai;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AiValidationCallRepository extends JpaRepository<AiValidationCall,UUID> {
    java.util.List<AiValidationCall> findByRequestIdAndSettlementState(String requestId,String settlementState);
    java.util.Optional<AiValidationCall> findFirstByRunIdAndRequestIdOrderByAttemptAsc(String runId,String requestId);
    java.util.List<AiValidationCall> findByRunIdAndSettlementStateIn(String runId,java.util.Collection<String> states);
    @org.springframework.data.jpa.repository.Query("select c.runId from AiValidationCall c where c.id=:id")
    java.util.Optional<String> runId(@org.springframework.data.repository.query.Param("id") UUID id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from AiValidationCall c where c.id=:id")
    java.util.Optional<AiValidationCall> lock(@org.springframework.data.repository.query.Param("id") UUID id);
}
