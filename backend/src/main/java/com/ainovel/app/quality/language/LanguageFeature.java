package com.ainovel.app.quality.language;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class LanguageFeature {
    private final LanguageSettingsRepository repository;
    private final boolean enabled;
    private final boolean generationEnabled;
    private final boolean diagnosisEnabled;
    public LanguageFeature(LanguageSettingsRepository repository,
            @Value("${app.language.enabled:false}") boolean enabled,
            @Value("${app.language.generation-enabled:true}") boolean generationEnabled,
            @Value("${app.language.diagnosis-enabled:true}") boolean diagnosisEnabled) {
        this.repository=repository; this.enabled=enabled; this.generationEnabled=generationEnabled; this.diagnosisEnabled=diagnosisEnabled;
    }
    public LanguageStandard.Selection selection(UUID storyId) {
        return new LanguageStandard.Selection(LanguageStandard.VERSION,generation(storyId),checkAfterGeneration(storyId));
    }
    public boolean enabled() { return enabled; }
    public boolean diagnosisEnabled() { return enabled && diagnosisEnabled; }
    public boolean generation(UUID storyId) { return enabled && generationEnabled && repository.findById(storyId).map(s->s.generationStandard).orElse(false); }
    public boolean checkAfterGeneration(UUID storyId) { return diagnosisEnabled() && repository.findById(storyId).map(s->s.checkAfterGeneration).orElse(false); }
    public boolean replacesLegacy(UUID storyId) { return generation(storyId) || checkAfterGeneration(storyId); }
    public LanguageDtos.Settings settings(UUID storyId) {
        var s=repository.findById(storyId).orElseGet(LanguageSettings::new);
        return new LanguageDtos.Settings(enabled,s.generationStandard,s.checkAfterGeneration,LanguageStandard.VERSION);
    }
    public LanguageDtos.Settings save(UUID storyId, LanguageDtos.SettingsRequest input) {
        var s=repository.findById(storyId).orElseGet(LanguageSettings::new);
        s.configure(storyId,input.generationStandard(),input.checkAfterGeneration()); repository.save(s);
        return settings(storyId);
    }
}
