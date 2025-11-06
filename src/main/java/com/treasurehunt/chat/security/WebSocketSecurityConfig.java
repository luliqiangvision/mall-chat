package com.treasurehunt.chat.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * WebSocket安全配置
 */
@Configuration
@EnableScheduling
public class WebSocketSecurityConfig {

    private final WebSocketRateLimiter rateLimiter;
    private final WebSocketConnectionManager connectionManager;

    public WebSocketSecurityConfig(WebSocketRateLimiter rateLimiter, 
                                 WebSocketConnectionManager connectionManager) {
        this.rateLimiter = rateLimiter;
        this.connectionManager = connectionManager;
    }

    /**
     * 定期清理过期的用户速率信息
     * 每5分钟执行一次
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void cleanupExpiredUsers() {
        rateLimiter.cleanupExpiredUsers();
    }

    /**
     * 定期输出连接统计信息
     * 每10分钟执行一次
     */
    @Scheduled(fixedRate = 10 * 60 * 1000)
    public void logConnectionStats() {
        WebSocketConnectionManager.ConnectionStats stats = connectionManager.getConnectionStats();
        if (stats.getTotalConnections() > 0) {
            System.out.println("WebSocket连接统计: " + stats.toString());
        }
    }
}
