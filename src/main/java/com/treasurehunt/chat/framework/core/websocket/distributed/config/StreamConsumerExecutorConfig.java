package com.treasurehunt.chat.framework.core.websocket.distributed.config;

import org.springframework.context.annotation.Bean;// 配置类里
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;


@Configuration
public class StreamConsumerExecutorConfig {

    @Bean("streamConsumerExecutor")
    public TaskExecutor streamConsumerExecutor() {
        var ex = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        ex.setCorePoolSize(1);
        ex.setMaxPoolSize(1);
        ex.setQueueCapacity(0);
        ex.setThreadNamePrefix("chat-stream-consumer-");
        ex.initialize();
        return ex;
    }
}