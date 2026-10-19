package com.ainovel.app.integration;

import com.ainovel.app.modelconfig.AiModelSelection;
import fireflychat.ai.v1.ChatCompletionsRequest;
import fireflychat.ai.v1.ThinkingEffort;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AiThinkingRequestTest {
    private AiModelSelection choice(String mode,String effort) {return new AiModelSelection("generic.chat","option","model","Plan",mode,effort,true,false,"TOKENS",null,null,null,1);}
    @Test void preservesPresenceAndSendsEffortOnlyWithExplicitEnable() {
        var base=ChatCompletionsRequest.newBuilder().setModel("model").build();
        var defaults=AiGatewayGrpcClient.withThinking(base,choice("DEFAULT",null));
        assertThat(defaults.hasThinkingEnabled()).isFalse();
        assertThat(defaults.getThinkingEffort()).isEqualTo(ThinkingEffort.THINKING_EFFORT_UNSPECIFIED);
        var off=AiGatewayGrpcClient.withThinking(base,choice("OFF",null));
        assertThat(off.hasThinkingEnabled()).isTrue();assertThat(off.getThinkingEnabled()).isFalse();
        var enabledDefault=AiGatewayGrpcClient.withThinking(base,choice("ON",null));
        assertThat(enabledDefault.getThinkingEnabled()).isTrue();assertThat(enabledDefault.getThinkingEffort()).isEqualTo(ThinkingEffort.THINKING_EFFORT_UNSPECIFIED);
        var high=AiGatewayGrpcClient.withThinking(base,choice("ON","HIGH"));
        assertThat(high.getThinkingEnabled()).isTrue();assertThat(high.getThinkingEffort()).isEqualTo(ThinkingEffort.THINKING_EFFORT_HIGH);
    }
}
