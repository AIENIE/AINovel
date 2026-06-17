package com.ainovel.app.style.model;

import com.ainovel.app.story.model.CharacterCard;
import com.ainovel.app.story.model.Story;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "character_voices", indexes = {
        @Index(name = "idx_character_voice_story", columnList = "story_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_character_voice_character", columnNames = "character_card_id")
})
public class CharacterVoice {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "character_card_id", nullable = false)
    private CharacterCard characterCard;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id", nullable = false)
    private Story story;

    @Column(name = "speech_pattern", columnDefinition = "longtext")
    private String speechPattern;

    @Column(name = "vocabulary_level", length = 50)
    private String vocabularyLevel;

    @Column(name = "catchphrases_json", columnDefinition = "longtext")
    private String catchphrasesJson;

    @Column(name = "emotional_range_json", columnDefinition = "longtext")
    private String emotionalRangeJson;

    @Column(length = 100)
    private String dialect;

    @Column(name = "sample_dialogues_json", columnDefinition = "longtext")
    private String sampleDialoguesJson;

    @Column(name = "ai_analysis_json", columnDefinition = "longtext")
    private String aiAnalysisJson;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public CharacterCard getCharacterCard() { return characterCard; }
    public void setCharacterCard(CharacterCard characterCard) { this.characterCard = characterCard; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public String getSpeechPattern() { return speechPattern; }
    public void setSpeechPattern(String speechPattern) { this.speechPattern = speechPattern; }
    public String getVocabularyLevel() { return vocabularyLevel; }
    public void setVocabularyLevel(String vocabularyLevel) { this.vocabularyLevel = vocabularyLevel; }
    public String getCatchphrasesJson() { return catchphrasesJson; }
    public void setCatchphrasesJson(String catchphrasesJson) { this.catchphrasesJson = catchphrasesJson; }
    public String getEmotionalRangeJson() { return emotionalRangeJson; }
    public void setEmotionalRangeJson(String emotionalRangeJson) { this.emotionalRangeJson = emotionalRangeJson; }
    public String getDialect() { return dialect; }
    public void setDialect(String dialect) { this.dialect = dialect; }
    public String getSampleDialoguesJson() { return sampleDialoguesJson; }
    public void setSampleDialoguesJson(String sampleDialoguesJson) { this.sampleDialoguesJson = sampleDialoguesJson; }
    public String getAiAnalysisJson() { return aiAnalysisJson; }
    public void setAiAnalysisJson(String aiAnalysisJson) { this.aiAnalysisJson = aiAnalysisJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
