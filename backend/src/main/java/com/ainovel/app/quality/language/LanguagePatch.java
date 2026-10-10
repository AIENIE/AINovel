package com.ainovel.app.quality.language;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="language_quality_patches")
public class LanguagePatch {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @Column(nullable=false) UUID reportId;
    @Column(nullable=false,length=80) String issueId;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") String dataJson;
    public void setDataJson(String value) { this.dataJson=value; }
    @Version long rowVersion;
    @Column(nullable=false) Instant createdAt=Instant.now();
}
