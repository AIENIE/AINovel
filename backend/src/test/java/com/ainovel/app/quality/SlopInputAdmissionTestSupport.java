package com.ainovel.app.quality;

import com.ainovel.app.ai.AiModelPolicy;
import com.ainovel.app.integration.AiGatewayGrpcClient;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

public final class SlopInputAdmissionTestSupport {
    private SlopInputAdmissionTestSupport() {}
    public static SlopInputAdmission create() {
        var admission = new SlopInputAdmission(20000,100000,24000);
        var gateway = mock(AiGatewayGrpcClient.class);
        when(gateway.chatCapabilities(anyString())).thenReturn(fireflychat.ai.v1.ModelChatCapabilities.getDefaultInstance());
        ReflectionTestUtils.setField(admission,"gateway",gateway);
        ReflectionTestUtils.setField(admission,"modelPolicy",new AiModelPolicy());
        return admission;
    }
}
