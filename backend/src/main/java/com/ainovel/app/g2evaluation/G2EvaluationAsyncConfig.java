package com.ainovel.app.g2evaluation;

import com.ainovel.app.common.MdcTaskDecorator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class G2EvaluationAsyncConfig {
    @Bean("g2EvaluationExecutor")
    public Executor g2EvaluationExecutor(MdcTaskDecorator mdcTaskDecorator) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(24);
        executor.setThreadNamePrefix("g2-evaluation-");
        executor.setTaskDecorator(mdcTaskDecorator);
        executor.initialize();
        return executor;
    }
}
