package com.treasurehunt.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket握手拦截器 - 用于调试
 */
@Slf4j
@Component
public class WebSocketHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        log.info("=== WebSocket握手开始 ===");
        log.info("Request URI: {}", request.getURI());
        log.info("Request Headers: {}", request.getHeaders());
        log.info("Remote Address: {}", request.getRemoteAddress());
        log.info("WebSocket Handler: {}", wsHandler.getClass().getSimpleName());
        
        // 记录所有请求头
        request.getHeaders().forEach((key, values) -> {
            log.info("Header {}: {}", key, values);
        });
        
        log.info("=== WebSocket握手结束 ===");
        return true; // 允许握手继续
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("WebSocket握手失败", exception);
        } else {
            log.info("WebSocket握手成功");
        }
    }
}
