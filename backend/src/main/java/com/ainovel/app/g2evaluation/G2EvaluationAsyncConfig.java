package com.ainovel.app.g2evaluation;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class G2EvaluationAsyncConfig {
    @Bean("g2EvaluationExecutor")
    public Executor g2EvaluationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(24);
        executor.setThreadNamePrefix("g2-evaluation-");
        executor.initialize();
        return executor;
    }
}
