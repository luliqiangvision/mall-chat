package com.treasurehunt.chat.framework.core.websocket.distributed.spi.impl;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.ServerWebSocketPool;
import com.treasurehunt.chat.framework.core.websocket.distributed.spi.ServerCommProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationMessage;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.SendResult;

import java.util.concurrent.CompletableFuture;

/**
 * WebSocket服务器间通信协议实现
 * 
 * 特点：
 * - 使用ServerWebSocketPool管理WebSocket连接池
 * - 支持长连接复用，性能更好
 * - 支持心跳检测和自动重连
 * - 完全绕过Spring WebSocket框架限制
 * 
 * @author gaga
 * @since 2025-01-24
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "server.comm.protocol", havingValue = "websocket", matchIfMissing = true)
public class WebSocketServerCommProtocol implements ServerCommProtocol {
    
    @Autowired
    private ServerWebSocketPool serverWebSocketPool;
    
    @Value("${websocket.instance.ip}")
    private String currentInstanceIp;
    
    @Value("${websocket.instance.port}")
    private int currentInstancePort;
    
    @Value("${websocket.distributed.server-comm.protocol:websocket}")
    private String protocolType;
    
    @Value("${websocket.distributed.server-comm.websocket.handshake-timeout:5000}")
    private int handshakeTimeout;
    
    @Value("${websocket.distributed.server-comm.websocket.heartbeat-interval:30000}")
    private int heartbeatInterval;
    
    @Value("${websocket.distributed.server-comm.websocket.heartbeat-timeout:10000}")
    private int heartbeatTimeout;
    
    @Override
    public CompletableFuture<SendResult> sendMessage(String targetInstanceAddress, NotificationMessage message) {
        try {
            // 检查是否支持目标实例（避免自己连接自己）
            if (!supportsTarget(targetInstanceAddress)) {
                log.debug("[ServerComm:WebSocket] Skipping self-connection: {}, conversationId={}, serverMsgId={}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId());
                return CompletableFuture.completedFuture(SendResult.fail(SendResult.SendCode.SELF_TARGET, "target is self", null));
            }
            
            // 使用ServerWebSocketPool发送消息
            boolean ok = serverWebSocketPool.sendMessage(targetInstanceAddress, message);
            return CompletableFuture.completedFuture(
                ok ? SendResult.ok(SendResult.SendCode.REMOTE_SENT, "[ServerComm:WebSocket] sent")
                   : SendResult.fail(SendResult.SendCode.CONNECT_FAIL, "[ServerComm:WebSocket] send returned false", null)
            );
                
        } catch (Exception e) {
            log.error("[ServerComm:WebSocket] Failed to send message to instance: {}, conversationId={}, serverMsgId={}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId(), e);
            return CompletableFuture.completedFuture(SendResult.fail(SendResult.SendCode.UNKNOWN_ERROR, e.getMessage(), e));
        }
    }
    
    @Override
    public void initialize() {
        log.info("Initializing WebSocket server communication protocol");
        // ServerWebSocketPool会自动初始化，这里不需要额外操作
    }
    
    @Override
    public void shutdown() {
        log.info("Shutting down WebSocket server communication protocol");
        // ServerWebSocketPool会自动管理生命周期，这里不需要额外操作
    }
    
    @Override
    public boolean isHealthy() {
        // 检查ServerWebSocketPool是否健康
        return serverWebSocketPool != null;
    }
    
    @Override
    public String getProtocolName() {
        return "websocket";
    }
    
    @Override
    public boolean supportsTarget(String targetInstanceAddress) {
        if (targetInstanceAddress == null) {
            return false;
        }
        
        try {
            String[] parts = targetInstanceAddress.split(":");
            if (parts.length != 2) {
                return false;
            }
            
            String targetIp = parts[0];
            int targetPort = Integer.parseInt(parts[1]);
            
            // 避免自己连接自己
            return !(targetIp.equals(currentInstanceIp) && targetPort == currentInstancePort);
            
        } catch (Exception e) {
            log.warn("Invalid target instance address format: {}", targetInstanceAddress);
            return false;
        }
    }
}
