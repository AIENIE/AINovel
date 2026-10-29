package com.ainovel.app.ai;
import jakarta.persistence.*;
@Entity @Table(name="ai_validation_budgets")
public class AiValidationBudget {
    @Id @Column(length=80) private String id;
    @Column(nullable=false) private int used;
    @Column(nullable=false) private int callLimit;
    private Integer providerAttemptLimit;
    @Column(nullable=false) private int reservedProviderAttempts;
    @Column(length=3) private String currency;
    @Column(precision=19,scale=6) private java.math.BigDecimal costLimit;
    @Column(nullable=false,precision=19,scale=6) private java.math.BigDecimal settledCost=java.math.BigDecimal.ZERO;
    @Column(nullable=false,precision=19,scale=6) private java.math.BigDecimal reservedCost=java.math.BigDecimal.ZERO;
    public String getCurrency(){return currency;}
    public void setCurrency(String value){currency=value;}
    public java.math.BigDecimal getCostLimit(){return costLimit;}
    public void setCostLimit(java.math.BigDecimal value){costLimit=value;}
    public java.math.BigDecimal getSettledCost(){return settledCost;}
    public void setSettledCost(java.math.BigDecimal value){settledCost=value;}
    public java.math.BigDecimal getReservedCost(){return reservedCost;}
    public void setReservedCost(java.math.BigDecimal value){reservedCost=value;}
    public Integer getProviderAttemptLimit(){return providerAttemptLimit;}
    public void setProviderAttemptLimit(Integer value){providerAttemptLimit=value;}
    public int getReservedProviderAttempts(){return reservedProviderAttempts;}
    public void setReservedProviderAttempts(int value){reservedProviderAttempts=value;}
    public String getId(){return id;}
    public void setId(String v){id=v;}
    public int getUsed(){return used;}
    public void setUsed(int v){used=v;}
    public int getCallLimit(){return callLimit;}
    public void setCallLimit(int v){callLimit=v;}
}
