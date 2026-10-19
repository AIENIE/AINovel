package com.ainovel.app.modelconfig;

import com.ainovel.app.common.CurrentUserResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class AiQuoteWebConfiguration {
    @Bean
    AiQuoteRequestFilter aiQuoteRequestFilter(AiActionPlanner planner,AiQuoteService quotes,CurrentUserResolver users,ObjectMapper json) {
        return new AiQuoteRequestFilter(planner,quotes,users,json);
    }
}
