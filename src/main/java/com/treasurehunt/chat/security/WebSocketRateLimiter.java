package com.treasurehunt.chat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket速率限制器
 * 防止用户发送消息过于频繁，防止DoS攻击
 */
@Slf4j
@Component
public class WebSocketRateLimiter {

    // 用户消息计数
    private final Map<String, UserRateInfo> userRateMap = new ConcurrentHashMap<>();
    
    // 全局消息计数
    private final AtomicInteger globalMessageCount = new AtomicInteger(0);
    private final AtomicLong globalResetTime = new AtomicLong(System.currentTimeMillis());
    
    // 配置参数
    private static final int MAX_MESSAGES_PER_MINUTE = 60; // 每分钟最大消息数
    private static final int MAX_MESSAGES_PER_HOUR = 1000; // 每小时最大消息数
    private static final int MAX_GLOBAL_MESSAGES_PER_MINUTE = 10000; // 全局每分钟最大消息数
    private static final long RATE_LIMIT_WINDOW = 60 * 1000; // 1分钟窗口
    private static final long HOUR_LIMIT_WINDOW = 60 * 60 * 1000; // 1小时窗口
    
    /**
     * 检查用户是否可以发送消息
     */
    public RateLimitResult checkRateLimit(String userId) {
        long currentTime = System.currentTimeMillis();
        
        // 检查全局限制
        if (!checkGlobalRateLimit(currentTime)) {
            log.warn("全局消息速率超限，用户: {}", userId);
            return RateLimitResult.exceeded("System busy, please try again later");
        }
        
        // 获取用户速率信息
        UserRateInfo userInfo = userRateMap.computeIfAbsent(userId, k -> new UserRateInfo());
        
        // 检查用户限制
        if (!userInfo.checkRateLimit(currentTime)) {
            log.warn("用户消息速率超限，用户: {}", userId);
            return RateLimitResult.exceeded();
        }
        
        // 更新计数
        userInfo.incrementMessageCount();
        globalMessageCount.incrementAndGet();
        
        return RateLimitResult.allowed();
    }
    
    /**
     * 检查全局速率限制
     */
    private boolean checkGlobalRateLimit(long currentTime) {
        long resetTime = globalResetTime.get();
        
        // 如果超过窗口时间，重置计数
        if (currentTime - resetTime > RATE_LIMIT_WINDOW) {
            if (globalResetTime.compareAndSet(resetTime, currentTime)) {
                globalMessageCount.set(0);
            }
        }
        
        return globalMessageCount.get() < MAX_GLOBAL_MESSAGES_PER_MINUTE;
    }
    
    /**
     * 清理过期的用户速率信息
     */
    public void cleanupExpiredUsers() {
        long currentTime = System.currentTimeMillis();
        userRateMap.entrySet().removeIf(entry -> {
            UserRateInfo userInfo = entry.getValue();
            return currentTime - userInfo.getLastMessageTime() > HOUR_LIMIT_WINDOW;
        });
    }
    
    /**
     * 用户速率信息
     */
    private static class UserRateInfo {
        private final AtomicInteger messageCount = new AtomicInteger(0);
        private final AtomicInteger hourMessageCount = new AtomicInteger(0);
        private final AtomicLong resetTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong hourResetTime = new AtomicLong(System.currentTimeMillis());
        private final AtomicLong lastMessageTime = new AtomicLong(System.currentTimeMillis());
        
        public boolean checkRateLimit(long currentTime) {
            // 检查分钟限制
            long resetTime = this.resetTime.get();
            if (currentTime - resetTime > RATE_LIMIT_WINDOW) {
                if (this.resetTime.compareAndSet(resetTime, currentTime)) {
                    messageCount.set(0);
                }
            }
            
            // 检查小时限制
            long hourResetTime = this.hourResetTime.get();
            if (currentTime - hourResetTime > HOUR_LIMIT_WINDOW) {
                if (this.hourResetTime.compareAndSet(hourResetTime, currentTime)) {
                    hourMessageCount.set(0);
                }
            }
            
            return messageCount.get() < MAX_MESSAGES_PER_MINUTE && 
                   hourMessageCount.get() < MAX_MESSAGES_PER_HOUR;
        }
        
        public void incrementMessageCount() {
            messageCount.incrementAndGet();
            hourMessageCount.incrementAndGet();
            lastMessageTime.set(System.currentTimeMillis());
        }
        
        public long getLastMessageTime() {
            return lastMessageTime.get();
        }
    }
    
    /**
     * 速率限制结果
     */
    public static class RateLimitResult {
        private final boolean allowed;
        private final String reason;
        
        private RateLimitResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static RateLimitResult allowed() {
            return new RateLimitResult(true, null);
        }
        
        public static RateLimitResult exceeded(String reason) {
            return new RateLimitResult(false, reason);
        }
        
        public static RateLimitResult exceeded() {
            return new RateLimitResult(false, "Message sent too frequently, please try again later");
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
}
