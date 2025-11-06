package com.treasurehunt.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.annotation.PreDestroy;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 聊天系统线程池配置类
 * 集中管理所有异步任务的线程池配置
 */
@Slf4j
@Configuration
public class ChatThreadPoolConfig {

    private ThreadPoolTaskExecutor conversationTaskExecutor;

    /**
     * 会话处理线程池
     * 用于会话创建、客服分配等异步任务
     */
    @Bean("conversationTaskExecutor")
    public TaskExecutor conversationTaskExecutor() {
        conversationTaskExecutor = new ThreadPoolTaskExecutor();
        conversationTaskExecutor.setCorePoolSize(5);
        conversationTaskExecutor.setMaxPoolSize(15);
        conversationTaskExecutor.setQueueCapacity(500);
        conversationTaskExecutor.setThreadNamePrefix("ChatConversation-");
        conversationTaskExecutor.setKeepAliveSeconds(120);
        conversationTaskExecutor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        conversationTaskExecutor.setWaitForTasksToCompleteOnShutdown(true);
        conversationTaskExecutor.setAwaitTerminationSeconds(60);
        conversationTaskExecutor.initialize();
        
        log.info("会话处理线程池初始化完成: core={}, max={}, queue={}", 
                conversationTaskExecutor.getCorePoolSize(),
                conversationTaskExecutor.getMaxPoolSize(),
                conversationTaskExecutor.getQueueCapacity());
        
        return conversationTaskExecutor;
    }

    /**
     * 优雅停机，关闭会话处理线程池
     */
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭会话处理线程池...");
        
        // 关闭前打印线程池状态
        printConversationThreadPoolStatus();
        
        shutdownExecutor("会话处理线程池", conversationTaskExecutor);
        
        // 关闭后再次打印状态
        log.info("=== 关闭后的会话处理线程池状态 ===");
        if (conversationTaskExecutor != null) {
            ThreadPoolExecutor executor = conversationTaskExecutor.getThreadPoolExecutor();
            log.info("最终状态: 活跃={}, 队列={}, 已完成={}", 
                    executor.getActiveCount(), 
                    executor.getQueue().size(),
                    executor.getCompletedTaskCount());
        }
        
        log.info("会话处理线程池已关闭");
    }

    /**
     * 关闭指定的线程池
     */
    private void shutdownExecutor(String name, ThreadPoolTaskExecutor executor) {
        if (executor != null) {
            try {
                log.info("正在关闭{}...", name);
                executor.shutdown();
                
                if (executor.getThreadPoolExecutor().awaitTermination(30, TimeUnit.SECONDS)) {
                    log.info("{}正常关闭", name);
                } else {
                    log.warn("{}等待超时，强制关闭", name);
                    executor.getThreadPoolExecutor().shutdownNow();
                }
            } catch (InterruptedException e) {
                log.warn("关闭{}时被中断，强制关闭", name);
                executor.getThreadPoolExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 获取会话处理线程池统计信息
     */
    public ConversationThreadPoolStats getConversationThreadPoolStats() {
        return new ConversationThreadPoolStats(
                getExecutorStats(conversationTaskExecutor, "会话处理")
        );
    }
    
    /**
     * 获取会话处理线程池的详细统计信息
     */
    public ConversationThreadPoolDetailStats getConversationThreadPoolDetailStats() {
        if (conversationTaskExecutor == null) {
            return new ConversationThreadPoolDetailStats(0, 0, 0, 0, 0, 0);
        }
        
        ThreadPoolExecutor executor = conversationTaskExecutor.getThreadPoolExecutor();
        return new ConversationThreadPoolDetailStats(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getCompletedTaskCount()
        );
    }
    
    /**
     * 打印会话处理线程池状态（用于优雅停机）
     */
    public void printConversationThreadPoolStatus() {
        ConversationThreadPoolDetailStats stats = getConversationThreadPoolDetailStats();
        
        log.info("=== 会话处理线程池状态 ===");
        log.info("核心线程数: {}", stats.getCorePoolSize());
        log.info("最大线程数: {}", stats.getMaxPoolSize());
        log.info("当前线程数: {}", stats.getCurrentPoolSize());
        log.info("活跃线程数: {}", stats.getActiveCount());
        log.info("队列中任务数: {}", stats.getQueueSize());
        log.info("已完成任务数: {}", stats.getCompletedTaskCount());
        log.info("====================");
    }

    /**
     * 获取单个线程池的统计信息
     */
    private ExecutorStats getExecutorStats(ThreadPoolTaskExecutor executor, String name) {
        if (executor == null) {
            return new ExecutorStats(name, 0, 0, 0, 0, 0);
        }
        
        ThreadPoolExecutor threadPool = executor.getThreadPoolExecutor();
        return new ExecutorStats(
                name,
                threadPool.getCorePoolSize(),
                threadPool.getMaximumPoolSize(),
                threadPool.getPoolSize(),
                threadPool.getActiveCount(),
                threadPool.getQueue().size()
        );
    }

    /**
     * 会话处理线程池统计信息
     */
    public static class ConversationThreadPoolStats {
        private final ExecutorStats conversationExecutor;
        
        public ConversationThreadPoolStats(ExecutorStats conversationExecutor) {
            this.conversationExecutor = conversationExecutor;
        }
        
        public ExecutorStats getConversationExecutor() { return conversationExecutor; }
    }
    
    /**
     * 会话处理线程池详细统计信息
     */
    public static class ConversationThreadPoolDetailStats {
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int currentPoolSize;
        private final int activeCount;
        private final int queueSize;
        private final long completedTaskCount;
        
        public ConversationThreadPoolDetailStats(int corePoolSize, int maxPoolSize, int currentPoolSize, 
                                     int activeCount, int queueSize, long completedTaskCount) {
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.currentPoolSize = currentPoolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
            this.completedTaskCount = completedTaskCount;
        }
        
        // Getters
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public int getActiveCount() { return activeCount; }
        public int getQueueSize() { return queueSize; }
        public long getCompletedTaskCount() { return completedTaskCount; }
    }

    /**
     * 单个线程池统计信息
     */
    public static class ExecutorStats {
        private final String name;
        private final int corePoolSize;
        private final int maxPoolSize;
        private final int currentPoolSize;
        private final int activeCount;
        private final int queueSize;

        public ExecutorStats(String name, int corePoolSize, int maxPoolSize, 
                           int currentPoolSize, int activeCount, int queueSize) {
            this.name = name;
            this.corePoolSize = corePoolSize;
            this.maxPoolSize = maxPoolSize;
            this.currentPoolSize = currentPoolSize;
            this.activeCount = activeCount;
            this.queueSize = queueSize;
        }

        // Getters
        public String getName() { return name; }
        public int getCorePoolSize() { return corePoolSize; }
        public int getMaxPoolSize() { return maxPoolSize; }
        public int getCurrentPoolSize() { return currentPoolSize; }
        public int getActiveCount() { return activeCount; }
        public int getQueueSize() { return queueSize; }
    }
}
