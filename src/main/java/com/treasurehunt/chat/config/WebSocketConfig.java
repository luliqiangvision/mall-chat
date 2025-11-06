package com.treasurehunt.chat.config;

import com.treasurehunt.chat.websocket.AgentWebSocketHandler;
import com.treasurehunt.chat.websocket.CustomerWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * WebSocket配置类
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private CustomerWebSocketHandler customerWebSocketHandler;

    @Autowired
    private AgentWebSocketHandler agentWebSocketHandler;

    @Autowired
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // 注册客户聊天WebSocket处理器
        registry.addHandler(customerWebSocketHandler, "/chat/customer-service")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*"); // 允许跨域

        // 注册客服聊天WebSocket处理器
        registry.addHandler(agentWebSocketHandler, "/chat/agent-service")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins("*"); // 允许跨域
    }

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
