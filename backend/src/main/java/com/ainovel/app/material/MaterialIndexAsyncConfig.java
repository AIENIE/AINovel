package com.ainovel.app.material;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;

@Configuration
public class MaterialIndexAsyncConfig {
    @Bean("materialIndexExecutor")
    public Executor materialIndexExecutor(@Value("${app.material.index.workers:2}") int workers,
                                          @Value("${app.material.index.queue-capacity:16}") int queueCapacity) {
        if (workers < 1 || queueCapacity < 1) throw new IllegalArgumentException("INVALID_MATERIAL_INDEX_CAPACITY");
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(workers); executor.setMaxPoolSize(workers); executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("material-index-");
        executor.initialize();
        return executor;
    }
}
