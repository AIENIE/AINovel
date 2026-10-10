package com.ainovel.app.quality.language;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="language_quality_decisions")
public class LanguageDecision {
    @Id @GeneratedValue(strategy=GenerationType.UUID) UUID id;
    @Column(nullable=false) UUID patchId;
    @Column(nullable=false,length=80) String requestKey;
    @Column(nullable=false,length=12) String action;
    @Column(nullable=false,length=64) String requestHash;
    @Lob @Column(nullable=false,columnDefinition="LONGTEXT") String resultJson;
    @Column(nullable=false) Instant createdAt=Instant.now();
}
