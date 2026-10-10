package com.ainovel.app.quality.language;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="language_quality_reports")
public class LanguageReport {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @Column(nullable=false) UUID manuscriptId;
    @Column(nullable=false) UUID sceneId;
    @Column(nullable=false,length=64) String sourceKey;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") String dataJson;
    public void setDataJson(String value) { this.dataJson=value; }
    @Version long rowVersion;
    @Column(nullable=false) Instant createdAt=Instant.now();
}
