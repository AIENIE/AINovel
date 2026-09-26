package com.ainovel.app.ai;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface AiValidationCallRepository extends JpaRepository<AiValidationCall,UUID> {}
