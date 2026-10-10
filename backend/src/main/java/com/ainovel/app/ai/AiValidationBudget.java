package com.ainovel.app.ai;
import jakarta.persistence.*;
@Entity @Table(name="ai_validation_budgets")
public class AiValidationBudget {
    @Id @Column(length=80) private String id;
    @Column(nullable=false) private int used;
    @Column(nullable=false) private int callLimit;
    private Integer providerAttemptLimit;
    @Column(nullable=false) private int reservedProviderAttempts;
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
