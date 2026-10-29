package com.ainovel.app.world.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="world_copy_receipts",uniqueConstraints=@UniqueConstraint(columnNames={"user_id","request_key"}))
@org.hibernate.annotations.Immutable
public class WorldCopyReceipt {
    @Id private UUID id;
    @Column(name="user_id",nullable=false) private UUID userId;
    @Column(name="request_key",nullable=false,length=100) private String requestKey;
    @Column(nullable=false,length=64) private String requestHash;
    @Column(nullable=false) private UUID sourceWorldId;
    @Column(nullable=false) private UUID copiedWorldId;
    @Column(nullable=false) private Instant createdAt;
    protected WorldCopyReceipt() {}
    public WorldCopyReceipt(UUID userId,String requestKey,String requestHash,UUID sourceWorldId,UUID copiedWorldId) {
        this.id=UUID.randomUUID();this.userId=userId;this.requestKey=requestKey;this.requestHash=requestHash;
        this.sourceWorldId=sourceWorldId;this.copiedWorldId=copiedWorldId;this.createdAt=Instant.now();
    }
    public String getRequestHash(){return requestHash;}
    public UUID getCopiedWorldId(){return copiedWorldId;}
}
