package com.ainovel.app.ai;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="ai_validation_calls",uniqueConstraints=@UniqueConstraint(columnNames={"run_id","attempt"}))
public class AiValidationCall {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false,length=80) private String runId;
    @Column(nullable=false) private int attempt;
    @Column(length=160) private String requestId;
    @Column(length=128) private String model;
    @Lob @Column(nullable=false) private String requestJson;
    @Lob private String resultJson;
    @Column(nullable=false,length=32) private String status;
    @Column(nullable=false) private Instant createdAt;
    @Column(length=32) private String operationKind;
    @Column(nullable=false) private int reservedProviderAttempts;
    public void setOperationKind(String value){operationKind=value;}
    public void setReservedProviderAttempts(int value){reservedProviderAttempts=value;}
    public UUID getId(){return id;}
    public void setRunId(String v){runId=v;}
    public void setAttempt(int v){attempt=v;}
    public void setRequestId(String v){requestId=v;}
    public void setModel(String v){model=v;}
    public void setRequestJson(String v){requestJson=v;}
    public void setResultJson(String v){resultJson=v;}
    public void setStatus(String v){status=v;}
    public void setCreatedAt(Instant v){createdAt=v;}
}
