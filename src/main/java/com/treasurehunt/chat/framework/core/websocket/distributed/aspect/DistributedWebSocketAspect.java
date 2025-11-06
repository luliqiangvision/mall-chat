package com.treasurehunt.chat.framework.core.websocket.distributed.aspect;


import lombok.extern.slf4j.Slf4j;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.UserSessionMetadataManager;
import com.treasurehunt.chat.vo.WebSocketUserInfo;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * 分布式WebSocket AOP切面
 * 自动拦截所有WebSocketHandler的生命周期方法，添加分布式会话管理功能
 * 
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(name = "websocket.distributed.enabled", havingValue = "true")
public class DistributedWebSocketAspect {
    
    @Autowired
    private UserSessionMetadataManager userSessionMetadataManager;

    /**
     * 显式配置的实例IP地址
     * 优先级最高，用于防止容器环境IP干扰
     * 类似 Dubbo 的配置方式
     */
    @Value("${websocket.instance.ip}")
    private String configuredInstanceIp;
    
    @Value("${spring.application.name}")
    private String applicationName;
    
    @Value("${server.port}")
    private String serverPort;
    
    /**
     * 连接建立后处理,在WebSocketHandler.afterConnectionEstablished方法执行后执行
     */
    @After("execution(* org.springframework.web.socket.WebSocketHandler.afterConnectionEstablished(..))")
    public void afterConnectionEstablished(JoinPoint joinPoint) {
        try {
            WebSocketSession session = (WebSocketSession) joinPoint.getArgs()[0];
            String sessionId = session.getId();
            String userId = extractUserId(session);
            String instanceAddress = getCurrentInstanceAddress();
            
            // 注册到用户会话元数据管理器
            if (userId != null) {
                userSessionMetadataManager.registerLocalSession(session);
                userSessionMetadataManager.registerUserSession(userId, sessionId, instanceAddress);
            }
            
            log.debug("分布式会话注册成功: sessionId={}, userId={}, instanceAddress={}", sessionId, userId, instanceAddress);
        } catch (Exception e) {
            log.error("分布式会话注册失败", e);
        }
    }
    
    /**
     * 连接关闭后处理
     */
    @After("execution(* org.springframework.web.socket.WebSocketHandler.afterConnectionClosed(..))")
    public void afterConnectionClosed(JoinPoint joinPoint) {
        try {
            WebSocketSession session = (WebSocketSession) joinPoint.getArgs()[0];
            String sessionId = session.getId();
            String userId = extractUserId(session);
            
            // 从用户会话元数据管理器移除
            if (userId != null) {
                userSessionMetadataManager.removeLocalSession(sessionId);
                userSessionMetadataManager.removeUserSession(userId, sessionId);
            }
            
            log.debug("分布式会话注销成功: sessionId={}, userId={}", sessionId, userId);
        } catch (Exception e) {
            log.error("分布式会话注销失败", e);
        }
    }
    
    /**
     * 传输错误后处理
     * 注意：传输错误不注销会话，因为连接可能仍然有效
     * 只有连接真正关闭时才注销会话
     */
    @After("execution(* org.springframework.web.socket.WebSocketHandler.handleTransportError(..))")
    public void handleTransportError(JoinPoint joinPoint) {
        try {
            WebSocketSession session = (WebSocketSession) joinPoint.getArgs()[0];
            // 传输错误时只记录日志，不注销会话,目前只打印日志
            log.warn("WebSocket传输错误: sessionId={}", session.getId());
        } catch (Exception e) {
            log.error("传输错误处理失败", e);
        }
    }

    /**
     * 这里是正常聊天的续期每次收到消息后，刷新会话 TTL 至 300 秒
     */
    @After("execution(* org.springframework.web.socket.WebSocketHandler.handleMessage(..))")
    public void afterHandleMessage(JoinPoint joinPoint) {
        try {
            WebSocketSession session = (WebSocketSession) joinPoint.getArgs()[0];
            String userId = extractUserId(session);
            String sessionId = session.getId();
            String instanceAddress = getCurrentInstanceAddress();
            
            // 通过心跳续期
            if (userId != null) {
                userSessionMetadataManager.heartbeat(userId, sessionId, instanceAddress);
            }
        } catch (Exception e) {
            log.debug("刷新会话TTL失败(消息处理后)", e);
        }
    }

    
    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }
    
    private String detectLocalIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> nics = java.net.NetworkInterface.getNetworkInterfaces();
            while (nics.hasMoreElements()) {
                java.net.NetworkInterface nic = nics.nextElement();
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                java.util.Enumeration<java.net.InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress() && addr.isSiteLocalAddress()) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
    
    /**
     * 从WebSocket会话中提取用户ID
     * @param session WebSocket会话
     * @return 用户ID
     */
    private String extractUserId(WebSocketSession session) {
        try {
            // 从session的attributes中获取用户信息对象
            Object userInfoObj = session.getAttributes().get("userInfo");
            if (userInfoObj instanceof WebSocketUserInfo) {
                WebSocketUserInfo userInfo = (WebSocketUserInfo) userInfoObj;
                return userInfo.getUserId();
            }
            
            // 兼容性：如果直接存储了userId
            Object userId = session.getAttributes().get("userId");
            if (userId != null) {
                return userId.toString();
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Failed to extract userId from session: {}", session.getId(), e);
            return null;
        }
    }
    
    /**
     * 获取当前实例地址 (IP:Port)
     * 优先级：显式配置 > 环境变量 > 自动检测 > localhost
     * @return 实例地址
     */
    private String getCurrentInstanceAddress() {
        String ip = null;
        
        // 1) 显式配置优先（类似 Dubbo 的配置方式）
        if (configuredInstanceIp != null && !configuredInstanceIp.trim().isEmpty()) {
            ip = configuredInstanceIp.trim();
            log.debug("Using configured instance IP: {}", ip);
        }
        
        // 2) 环境变量（容器/编排环境）
        if (ip == null || ip.isEmpty()) {
            ip = firstNonEmpty(System.getenv("POD_IP"), System.getenv("HOST_IP"), System.getenv("INSTANCE_IP"));
            if (ip != null && !ip.isEmpty()) {
                log.debug("Using environment variable IP: {}", ip);
            }
        }
        
        // 3) 自动检测本地IP
        if (ip == null || ip.isEmpty()) {
            ip = detectLocalIp();
            if (ip != null && !ip.isEmpty()) {
                log.debug("Using detected local IP: {}", ip);
            }
        }
        
        // 4) 兜底使用 localhost
        if (ip == null || ip.isEmpty()) {
            ip = "localhost";
            log.debug("Using fallback localhost IP");
        }
        
        String instanceAddress = ip + ":" + serverPort;
        log.debug("Final instance address: {}", instanceAddress);
        return instanceAddress;
    }
}
