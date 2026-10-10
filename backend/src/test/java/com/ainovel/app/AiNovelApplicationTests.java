package com.ainovel.app;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiNovelApplicationTests {
    // Isolate business tests from the external SSO transport and its mounted CA.
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.ainovel.app.auth.SsoTokenExchangeService ssoTokenExchangeService;


    @Test
    void contextLoads() {
    }
}
