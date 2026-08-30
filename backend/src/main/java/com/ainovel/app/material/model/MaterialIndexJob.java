package com.ainovel.app.material.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="material_index_jobs", uniqueConstraints=@UniqueConstraint(name="uk_material_index_job_material",columnNames="material_id"))
public class MaterialIndexJob {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @OneToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="material_id") private Material material;
    @Column(nullable=false,length=24) private String status;
    private int attemptCount; private Instant nextAttemptAt;
    @Column(length=100) private String leaseOwner; private Instant leaseExpiresAt;
    @Column(length=100) private String errorCode;
    @CreationTimestamp private Instant createdAt; @UpdateTimestamp private Instant updatedAt;
    public UUID getId(){return id;} public Material getMaterial(){return material;} public void setMaterial(Material v){material=v;}
    public String getStatus(){return status;} public void setStatus(String v){status=v;}
    public int getAttemptCount(){return attemptCount;} public void setAttemptCount(int v){attemptCount=v;}
    public Instant getNextAttemptAt(){return nextAttemptAt;} public void setNextAttemptAt(Instant v){nextAttemptAt=v;}
    public String getLeaseOwner(){return leaseOwner;} public void setLeaseOwner(String v){leaseOwner=v;}
    public Instant getLeaseExpiresAt(){return leaseExpiresAt;} public void setLeaseExpiresAt(Instant v){leaseExpiresAt=v;}
    public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
}
