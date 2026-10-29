package com.ainovel.app.quality;

import com.ainovel.app.ai.dto.AiChatRequest;
import com.ainovel.app.quality.language.LanguageAnalysis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SlopInputAdmission {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.integration.AiGatewayGrpcClient gateway;
    @org.springframework.beans.factory.annotation.Autowired
    private com.ainovel.app.ai.AiModelPolicy modelPolicy;
    private final int maxMessage,maxTotal,inputTokens;
    public SlopInputAdmission(@Value("${app.ai.admission.max-message-chars:20000}") int maxMessage,
                             @Value("${app.ai.admission.max-total-chars:100000}") int maxTotal,
                             @Value("${app.slop.input-token-budget:24000}") int inputTokens) {
        this.maxMessage=maxMessage; this.maxTotal=maxTotal; this.inputTokens=inputTokens;
    }
    public void requireFits(AiChatRequest request) {
        if(!LanguageAnalysis.fits(request,maxMessage,maxTotal,inputTokens)) throw new InputLimit();
        if(gateway!=null) {
            var selected=com.ainovel.app.modelconfig.AiSelectionContext.current()==null?null:com.ainovel.app.modelconfig.AiSelectionContext.selection(request.featureKey());
            var caps=gateway.chatCapabilities(selected==null?modelPolicy.modelKey():selected.modelKey());
            if(caps.getMaxPromptTokens()>0 && !LanguageAnalysis.fits(request,maxMessage,maxTotal,(int)Math.min(Integer.MAX_VALUE,caps.getMaxPromptTokens()))) throw new InputLimit();
        }
    }
    public static class InputLimit extends RuntimeException {
        public InputLimit() { super("正文未检查：输入超限"); }
    }
}
