package com.ainovel.app.manuscript.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "manuscript_creation_receipts", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "outline_id", "request_key"}))
public class ManuscriptCreationReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private UUID userId;
    @Column(nullable = false) private UUID outlineId;
    @Column(nullable = false, length = 36) private String requestKey;
    @Column(nullable = false, length = 64) private String requestHash;
    private UUID manuscriptId;
    @Lob @Column(nullable = false) private String responseJson;

    public ManuscriptCreationReceipt() {}
    public ManuscriptCreationReceipt(UUID userId, UUID outlineId, String key, String hash, UUID manuscriptId, String responseJson) {
        this.userId = userId; this.outlineId = outlineId; this.requestKey = key;
        this.requestHash = hash; this.manuscriptId = manuscriptId; this.responseJson = responseJson;
    }
    public String getRequestHash() { return requestHash; }
    public UUID getManuscriptId() { return manuscriptId; }
    public String getResponseJson() { return responseJson; }
}
