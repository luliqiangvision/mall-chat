package com.treasurehunt.chat.framework.core.websocket.distributed.spi.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.distributed.spi.ServerCommProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationMessage;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.SendResult;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP/2服务器间通信协议实现
 * 
 * 特点：
 * - 使用Java标准库HTTP/2客户端
 * - 更好的网络兼容性，防火墙友好
 * - 支持连接复用和流复用
 * - 更成熟的监控和调试工具
 * 
 * @author gaga
 * @since 2025-01-24
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "server.comm.protocol", havingValue = "http2")
public class Http2ServerCommProtocol implements ServerCommProtocol {
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Value("${websocket.instance.ip}")
    private String currentInstanceIp;
    
    @Value("${websocket.instance.port}")
    private int currentInstancePort;
    
    @Value("${websocket.distributed.server-comm.protocol:websocket}")
    private String protocolType;
    
    @Value("${websocket.distributed.server-comm.http2.connect-timeout:3000}")
    private int connectTimeout;
    
    @Value("${websocket.distributed.server-comm.http2.request-timeout:5000}")
    private int requestTimeout;
    
    @Value("${websocket.distributed.server-comm.http2.retry:5}")
    private int maxRetries;
    
    @Value("${websocket.distributed.server-comm.http2.endpoint:/server/push}")
    private String pushEndpoint;
    
    // HTTP/2客户端
    private HttpClient httpClient;
    
    @Override
    public CompletableFuture<SendResult> sendMessage(String targetInstanceAddress, NotificationMessage message) {
        try {
            // 检查是否支持目标实例（避免自己连接自己）
            if (!supportsTarget(targetInstanceAddress)) {
                log.debug("[ServerComm:HTTP2] Skipping self-connection: {}, conversationId={}, serverMsgId={}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId());
                return CompletableFuture.completedFuture(SendResult.fail(SendResult.SendCode.SELF_TARGET, "target is self", null));
            }
            
            String messageJson = objectMapper.writeValueAsString(message);
            String url = "http://" + targetInstanceAddress + pushEndpoint;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("User-Agent", "mall-chat-server-comm")
                .POST(HttpRequest.BodyPublishers.ofString(messageJson))
                .timeout(Duration.ofMillis(requestTimeout))
                .build();
            
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    boolean success = response.statusCode() == 200;
                    if (success) {
                        log.debug("[ServerComm:HTTP2] Message sent successfully to instance: {}", targetInstanceAddress);
                        return SendResult.ok(SendResult.SendCode.REMOTE_SENT, "[ServerComm:HTTP2] sent");
                    } else {
                        log.error("[ServerComm:HTTP2] Failed to send message to instance: {}, conversationId={}, serverMsgId={}, status: {}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId(),response.statusCode());
                        return SendResult.fail(SendResult.SendCode.CONNECT_FAIL, "status="+response.statusCode(), null);
                    }
                })
                .exceptionally(throwable -> {
                    log.error("[ServerComm:HTTP2] Error sending message to instance: {}, conversationId={}, serverMsgId={}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId(), throwable);
                    return SendResult.fail(SendResult.SendCode.UNKNOWN_ERROR, throwable.getMessage(), throwable);
                });
                
        } catch (Exception e) {
            log.error("[ServerComm:HTTP2] Failed to send message to instance: {}, conversationId={}, serverMsgId={}", targetInstanceAddress, message.getConversationId(),message.getServerMsgId(), e);
            return CompletableFuture.completedFuture(SendResult.fail(SendResult.SendCode.UNKNOWN_ERROR, e.getMessage(), e));
        }
    }
    
    @Override
    public void initialize() {
        log.info("Initializing HTTP/2 server communication protocol");
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofMillis(connectTimeout))
            .build();
    }
    
    @Override
    public void shutdown() {
        log.info("Shutting down HTTP/2 server communication protocol");
        // HTTP/2客户端会自动管理连接，无需手动关闭
    }
    
    @Override
    public boolean isHealthy() {
        // HTTP/2客户端总是健康的，连接由客户端自动管理
        return true;
    }
    
    @Override
    public String getProtocolName() {
        return "http2";
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
