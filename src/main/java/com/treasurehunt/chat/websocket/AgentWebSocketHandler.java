package com.treasurehunt.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.dispatcher.WebSocketDispatcher;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import com.treasurehunt.chat.security.WebSocketConnectionManager;
import com.treasurehunt.chat.security.WebSocketRateLimiter;
import com.treasurehunt.chat.security.WebSocketSecurityFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * 客服聊天WebSocket处理器
 * 专门处理客服人员的聊天连接
 */
@Slf4j
@Component
public class AgentWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();
    
    
    @Autowired
    private WebSocketSecurityFilter securityFilter;
    
    @Autowired
    private WebSocketRateLimiter rateLimiter;
    
    @Autowired
    private WebSocketConnectionManager connectionManager;
    
    @Autowired
    private WebSocketDispatcher webSocketDispatcher;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket连接建立: {}", session.getId());
        // 网关已经完成认证并透传用户信息，直接从握手头获取
        String userId = session.getHandshakeHeaders().getFirst("X-User-Id");
        String userType = session.getHandshakeHeaders().getFirst("X-User-Type");
        if (userId == null || userType == null || !"agent".equals(userType)) {
            log.warn("WebSocket连接缺少客服用户信息或用户类型不正确，关闭连接");
            session.close(CloseStatus.BAD_DATA.withReason("Missing or incorrect agent service info"));
            return;
        }
        // 获取客户端IP
        String clientIp = connectionManager.getClientIp(session);
        // 检查连接限制（只记录，不拒绝连接）
        WebSocketConnectionManager.ConnectionCheckResult connectionResult = 
            connectionManager.checkConnection(userId, clientIp);
        if (!connectionResult.isAllowed()) {
            log.warn("客服WebSocket连接超限但允许连接: {}, 原因: {}", userId, connectionResult.getReason());
            // 将连接限制信息存储到session中，用于后续消息发送时的限制
            session.getAttributes().put("connectionLimited", true);
            session.getAttributes().put("connectionLimitReason", connectionResult.getReason());
        }
        // 注册连接
        connectionManager.registerConnection(session.getId(), userId, clientIp);
        // 创建用户信息对象
        WebSocketUserInfo userInfo = new WebSocketUserInfo(userId, userType);
        log.info("客服WebSocket连接用户信息 - 用户ID: {}, 用户类型: {}", userId, userType);
        // 存储客服会话
        SessionManager.addCustomerServiceSession(userId, session);
        log.info("客服连接: {}", userId);
        // 将用户信息存储到session属性中
        session.getAttributes().put("userInfo", userInfo);
    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // 暂时只处理文本消息,那种需要实时性很高的直播,后续再说,目前暂时用不到
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            log.info("收到消息: {}", payload);
            try {
                // 解析为 WebSocketRequest
                WebSocketDataWrapper<String> request = objectMapper.readValue(payload, WebSocketDataWrapper.class);
                // 使用路由处理器处理消息
                webSocketDispatcher.dispatch(request, session);
            } catch (Exception e) {
                log.error("处理客服消息失败", e);
                WebSocketDataWrapper.failure("error", "消息格式错误", "500");
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("客服WebSocket传输错误", exception);
        SessionManager.removeCustomerServiceSession(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("客服WebSocket连接关闭: {}, 状态: {}", session.getId(), closeStatus);
        // 获取用户信息并注销连接
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo != null) {
            String clientIp = connectionManager.getClientIp(session);
            connectionManager.unregisterConnection(session.getId(), userInfo.getUserId(), clientIp);
        }
        SessionManager.removeCustomerServiceSession(session);
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
