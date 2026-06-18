package com.ainovel.app.v2.model;

import com.ainovel.app.manuscript.model.Manuscript;
import com.ainovel.app.story.model.Story;
import com.ainovel.app.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "export_jobs")
public class V2ExportJob {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "story_id")
    private Story story;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manuscript_id")
    private Manuscript manuscript;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private V2ExportTemplate template;
    @Column(nullable = false, length = 16)
    private String format;
    @Lob
    private String configJson;
    @Column(length = 200)
    private String chapterRange;
    @Column(nullable = false, length = 32)
    private String status;
    @Column(nullable = false)
    private int progress;
    @Column(length = 500)
    private String filePath;
    @Column(length = 300)
    private String fileName;
    private Long fileSizeBytes;
    @Lob
    private String errorMessage;
    private Instant expiresAt;
    @CreationTimestamp
    private Instant createdAt;

    public UUID getId() { return id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public Story getStory() { return story; }
    public void setStory(Story story) { this.story = story; }
    public Manuscript getManuscript() { return manuscript; }
    public void setManuscript(Manuscript manuscript) { this.manuscript = manuscript; }
    public V2ExportTemplate getTemplate() { return template; }
    public void setTemplate(V2ExportTemplate template) { this.template = template; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }
    public String getChapterRange() { return chapterRange; }
    public void setChapterRange(String chapterRange) { this.chapterRange = chapterRange; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getProgress() { return progress; }
    public void setProgress(int progress) { this.progress = progress; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Instant getCreatedAt() { return createdAt; }
}
