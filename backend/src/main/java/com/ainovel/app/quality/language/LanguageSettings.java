package com.ainovel.app.quality.language;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="language_quality_settings")
public class LanguageSettings {
    @Id UUID storyId;
    boolean generationStandard;
    boolean checkAfterGeneration;
    public void configure(UUID storyId,boolean generationStandard,boolean checkAfterGeneration) {
        this.storyId=storyId; this.generationStandard=generationStandard; this.checkAfterGeneration=checkAfterGeneration;
    }
}
