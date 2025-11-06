package com.treasurehunt.chat.component.async;

import com.treasurehunt.chat.config.ChatThreadPoolConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

import java.util.concurrent.RejectedExecutionException;

/**
 * 聊天系统异步任务执行器
 * 基于线程池的异步任务执行，替代@Async注解
 */
@Slf4j
@Component
public class ChatAsyncExecutor {

    @Autowired
    @Qualifier("conversationTaskExecutor")
    private TaskExecutor conversationTaskExecutor;

    @Autowired
    private ChatThreadPoolConfig threadPoolConfig;

    /**
     * 异步执行会话处理任务
     * 
     * @param task 任务
     * @param taskName 任务名称（用于日志）
     */
    public void executeConversationTask(Runnable task, String taskName) {
        try {
            conversationTaskExecutor.execute(() -> {
                try {
                    log.info("🚀 开始执行会话处理任务: {}", taskName);
                    task.run();
                    log.info("✅ 会话处理任务完成: {}", taskName);
                } catch (Exception e) {
                    log.error("❌ 会话处理任务失败: {}", taskName, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("线程池已满或已关闭，拒绝会话处理任务: {}, error: {}", taskName, e.getMessage());
            // 可以选择降级处理或告警
            handleTaskRejection(task, taskName);
        }
    }

    /**
     * 异步执行会话处理任务（带参数）
     * 
     * @param task 任务
     * @param taskName 任务名称
     * @param params 任务参数（用于日志）
     */
    public void executeConversationTask(Runnable task, String taskName, Object... params) {
        String fullTaskName = taskName + "(" + String.join(", ", String.valueOf(params)) + ")";
        executeConversationTask(task, fullTaskName);
    }

    /**
     * 处理任务被拒绝的情况
     * 
     * @param task 被拒绝的任务
     * @param taskName 任务名称
     */
    private void handleTaskRejection(Runnable task, String taskName) {
        log.warn("任务被拒绝，尝试降级处理: {}", taskName);
        
        // 打印线程池状态
        ChatThreadPoolConfig.ConversationThreadPoolDetailStats stats = 
            threadPoolConfig.getConversationThreadPoolDetailStats();
        log.warn("当前线程池状态: 活跃={}, 队列={}, 已完成={}", 
                stats.getActiveCount(), stats.getQueueSize(), stats.getCompletedTaskCount());
        
        // 可以选择以下降级策略之一：
        // 1. 同步执行（阻塞当前线程）
        // 2. 丢弃任务
        // 3. 记录到队列等待重试
        
        // 这里选择同步执行作为降级策略
        try {
            log.warn("降级为同步执行: {}", taskName);
            task.run();
            log.info("降级同步执行完成: {}", taskName);
        } catch (Exception e) {
            log.error("降级同步执行失败: {}", taskName, e);
        }
    }

    /**
     * 获取线程池状态
     */
    public ChatThreadPoolConfig.ConversationThreadPoolStats getThreadPoolStats() {
        return threadPoolConfig.getConversationThreadPoolStats();
    }

    /**
     * 打印线程池状态
     */
    public void printThreadPoolStatus() {
        threadPoolConfig.printConversationThreadPoolStatus();
    }
}
