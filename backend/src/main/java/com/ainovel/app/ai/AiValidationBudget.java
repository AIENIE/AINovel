package com.ainovel.app.ai;
import jakarta.persistence.*;
@Entity @Table(name="ai_validation_budgets")
public class AiValidationBudget {
    @Id @Column(length=80) private String id;
    @Column(nullable=false) private int used;
    @Column(nullable=false) private int callLimit;
    public String getId(){return id;}
    public void setId(String v){id=v;}
    public int getUsed(){return used;}
    public void setUsed(int v){used=v;}
    public int getCallLimit(){return callLimit;}
    public void setCallLimit(int v){callLimit=v;}
}
