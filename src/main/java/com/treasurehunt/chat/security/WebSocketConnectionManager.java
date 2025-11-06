package com.treasurehunt.chat.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * WebSocket连接管理器
 * 管理连接数量、IP限制、会话验证等
 */
@Slf4j
@Component
public class WebSocketConnectionManager {

    // 用户连接计数
    private final Map<String, AtomicInteger> userConnectionCount = new ConcurrentHashMap<>();
    
    // IP连接计数
    private final Map<String, AtomicInteger> ipConnectionCount = new ConcurrentHashMap<>();
    
    // 会话验证
    private final Map<String, String> sessionUserMap = new ConcurrentHashMap<>();
    
    // 配置参数
    private static final int MAX_CONNECTIONS_PER_USER = 5; // 每个用户最大连接数（放宽限制）
    private static final int MAX_CONNECTIONS_PER_IP = 15; // 每个IP最大连接数（放宽限制）
    private static final int MAX_TOTAL_CONNECTIONS = 10000; // 系统最大连接数
    
    // 当前总连接数
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    
    /**
     * 检查是否可以建立新连接
     */
    public ConnectionCheckResult checkConnection(String userId, String clientIp) {
        // 检查总连接数
        if (totalConnections.get() >= MAX_TOTAL_CONNECTIONS) {
            log.warn("系统连接数已达上限: {}", totalConnections.get());
            return ConnectionCheckResult.rejected("System connection limit reached");
        }
        
        // 检查用户连接数
        int userConnections = userConnectionCount
            .computeIfAbsent(userId, k -> new AtomicInteger(0))
            .get();
        
        if (userConnections >= MAX_CONNECTIONS_PER_USER) {
            log.warn("用户连接数超限: {}, 用户: {}", userConnections, userId);
            return ConnectionCheckResult.rejected("User connection limit exceeded");
        }
        
        // 检查IP连接数
        int ipConnections = ipConnectionCount
            .computeIfAbsent(clientIp, k -> new AtomicInteger(0))
            .get();
        
        if (ipConnections >= MAX_CONNECTIONS_PER_IP) {
            log.warn("IP连接数超限: {}, IP: {}", ipConnections, clientIp);
            return ConnectionCheckResult.rejected("IP connection limit exceeded");
        }
        
        return ConnectionCheckResult.allowed();
    }
    
    /**
     * 注册新连接
     */
    public void registerConnection(String sessionId, String userId, String clientIp) {
        // 增加计数
        userConnectionCount.computeIfAbsent(userId, k -> new AtomicInteger(0)).incrementAndGet();
        ipConnectionCount.computeIfAbsent(clientIp, k -> new AtomicInteger(0)).incrementAndGet();
        totalConnections.incrementAndGet();
        
        // 记录会话映射
        sessionUserMap.put(sessionId, userId);
        
        log.info("注册WebSocket连接: sessionId={}, userId={}, clientIp={}, 总连接数={}", 
                sessionId, userId, clientIp, totalConnections.get());
    }
    
    /**
     * 注销连接
     */
    public void unregisterConnection(String sessionId, String userId, String clientIp) {
        // 减少计数
        AtomicInteger userCount = userConnectionCount.get(userId);
        if (userCount != null) {
            int newCount = userCount.decrementAndGet();
            if (newCount <= 0) {
                userConnectionCount.remove(userId);
            }
        }
        
        AtomicInteger ipCount = ipConnectionCount.get(clientIp);
        if (ipCount != null) {
            int newCount = ipCount.decrementAndGet();
            if (newCount <= 0) {
                ipConnectionCount.remove(clientIp);
            }
        }
        
        totalConnections.decrementAndGet();
        
        // 移除会话映射
        sessionUserMap.remove(sessionId);
        
        log.info("注销WebSocket连接: sessionId={}, userId={}, clientIp={}, 总连接数={}", 
                sessionId, userId, clientIp, totalConnections.get());
    }
    
    /**
     * 验证会话
     */
    public boolean validateSession(String sessionId, String userId) {
        String mappedUserId = sessionUserMap.get(sessionId);
        return userId.equals(mappedUserId);
    }
    
    /**
     * 获取客户端IP
     */
    public String getClientIp(WebSocketSession session) {
        // 尝试从请求头获取真实IP
        String xForwardedFor = session.getHandshakeHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = session.getHandshakeHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        // 从远程地址获取
        if (session.getRemoteAddress() != null) {
            return session.getRemoteAddress().getAddress().getHostAddress();
        }
        
        return "unknown";
    }
    
    /**
     * 获取连接统计信息
     */
    public ConnectionStats getConnectionStats() {
        return new ConnectionStats(
            totalConnections.get(),
            userConnectionCount.size(),
            ipConnectionCount.size(),
            sessionUserMap.size()
        );
    }
    
    /**
     * 连接检查结果
     */
    public static class ConnectionCheckResult {
        private final boolean allowed;
        private final String reason;
        
        private ConnectionCheckResult(boolean allowed, String reason) {
            this.allowed = allowed;
            this.reason = reason;
        }
        
        public static ConnectionCheckResult allowed() {
            return new ConnectionCheckResult(true, null);
        }
        
        public static ConnectionCheckResult rejected(String reason) {
            return new ConnectionCheckResult(false, reason);
        }
        
        public boolean isAllowed() {
            return allowed;
        }
        
        public String getReason() {
            return reason;
        }
    }
    
    /**
     * 连接统计信息
     */
    public static class ConnectionStats {
        private final int totalConnections;
        private final int uniqueUsers;
        private final int uniqueIps;
        private final int activeSessions;
        
        public ConnectionStats(int totalConnections, int uniqueUsers, int uniqueIps, int activeSessions) {
            this.totalConnections = totalConnections;
            this.uniqueUsers = uniqueUsers;
            this.uniqueIps = uniqueIps;
            this.activeSessions = activeSessions;
        }
        
        public int getTotalConnections() {
            return totalConnections;
        }
        
        public int getUniqueUsers() {
            return uniqueUsers;
        }
        
        public int getUniqueIps() {
            return uniqueIps;
        }
        
        public int getActiveSessions() {
            return activeSessions;
        }
        
        @Override
        public String toString() {
            return String.format("ConnectionStats{totalConnections=%d, uniqueUsers=%d, uniqueIps=%d, activeSessions=%d}",
                    totalConnections, uniqueUsers, uniqueIps, activeSessions);
        }
    }
}
