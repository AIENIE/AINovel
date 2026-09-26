package com.ainovel.app.narrative;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface NarrativeContextSettingsRepository extends JpaRepository<NarrativeContextSettings, UUID> {}
