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
    @Column(length=160) private String budgetId;
    public String getBudgetId(){return budgetId;}
    public void setBudgetId(String value){budgetId=value;}
    @Column(length=64) private String requestHash;
    public String getRequestHash(){return requestHash;}
    public void setRequestHash(String value){requestHash=value;}
    @Column(length=3) private String currency;
    public String getCurrency(){return currency;}
    public void setCurrency(String value){currency=value;}
     private Long priceVersionId;
    public Long getPriceVersionId(){return priceVersionId;}
    public void setPriceVersionId(Long value){priceVersionId=value;}
    @Column(length=64) private String priceSha256;
    public String getPriceSha256(){return priceSha256;}
    public void setPriceSha256(String value){priceSha256=value;}
    @Column(precision=19,scale=6) private java.math.BigDecimal maxCost;
    public java.math.BigDecimal getMaxCost(){return maxCost;}
    public void setMaxCost(java.math.BigDecimal value){maxCost=value;}
    @Column(precision=19,scale=6) private java.math.BigDecimal settledCost;
    public java.math.BigDecimal getSettledCost(){return settledCost;}
    public void setSettledCost(java.math.BigDecimal value){settledCost=value;}
    @Column(length=32) private String settlementState;
    public String getSettlementState(){return settlementState;}
    public void setSettlementState(String value){settlementState=value;}
    public String getRunId(){return runId;}
    public String getRequestId(){return requestId;}
    public String getRequestJson(){return requestJson;}
    public String getResultJson(){return resultJson;}
    public String getStatus(){return status;}
    public String getModel(){return model;}
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
