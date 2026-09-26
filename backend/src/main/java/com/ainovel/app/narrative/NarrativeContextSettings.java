package com.ainovel.app.narrative;
import jakarta.persistence.*;
import java.util.UUID;
@Entity @Table(name="narrative_context_settings")
public class NarrativeContextSettings {
    @Id private UUID storyId;
    @Column(nullable=false) private boolean enabled;
    @Column(nullable=false) private long revision;
    @Version private long lockVersion;
    public UUID getStoryId() { return storyId; }
    public void setStoryId(UUID value) { storyId=value; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled=value; }
    public long getRevision() { return revision; }
    public void setRevision(long value) { revision=value; }
}
