package com.ainovel.app.modelconfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable execution and retail-price snapshot. Provider prices are deliberately separate. */
public record AiModelSelection(String featureKey, String optionId, String modelKey, String label,
        String thinkingMode, String thinkingEffort, boolean streaming, boolean billingEnabled,
        String pricingMode, Long fixedCredits, BigDecimal inputPer1k, BigDecimal outputPer1k,
        long revision) {
    public long cost(long input, long output) {
        if (!billingEnabled) return 0;
        if ("FIXED".equals(pricingMode)) return fixedCredits;
        return Math.max(1, inputPer1k.multiply(BigDecimal.valueOf(Math.max(0,input)))
                .add(outputPer1k.multiply(BigDecimal.valueOf(Math.max(0,output))))
                .divide(BigDecimal.valueOf(1000),0,RoundingMode.CEILING).longValueExact());
    }
    public static AiModelSelection legacy(String model, String label) {
        return new AiModelSelection("legacy",null,model,label,"DEFAULT",null,true,true,
                "TOKENS",null,new BigDecimal("0.01"),new BigDecimal("0.01"),0);
    }
}
