package com.treasurehunt.chat.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.dispatcher.WebSocketDispatcher;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import com.treasurehunt.chat.security.WebSocketConnectionManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

/**
 * 客户聊天WebSocket处理器,websocket是有状态的协议,跟http不同,所以我们这里不能省略掉当前这个类,无法完全跟springmvc一样
 * 专门处理普通用户的聊天连接
 *
 * 这部分可以去看自研websocket springmvc 小框架的文档
 * WebSocket 在协议层（RFC 6455）只有一次握手有 HTTP header，握手完成后传输阶段是“纯帧结构”，再也没有 header 概念。
 * 一、协议分两层看
 * | 阶段 | 协议层 | 内容 |
 * | ------------ | ------------------- |
 * ------------------------------------------------------------------------------------------------------------
 * |
 * | **1. 握手阶段** | **HTTP 协议** | 客户端用 HTTP 请求发起升级 (`Upgrade: websocket`)，这里确实有
 * HTTP header，例如：`Host`、`Sec-WebSocket-Key`、`Origin` 等。 |
 * | **2. 数据帧阶段** | **WebSocket 自身帧协议** | 握手完成后，进入“帧传输”模式（Frame），帧头部只有
 * `FIN`、`opcode`、`mask`、`payload length`、`masking-key`，**没有 header 或 method
 * 概念**。 |
 */
@Slf4j
@Component
public class CustomerWebSocketHandler implements WebSocketHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebSocketConnectionManager connectionManager;

    @Autowired
    private WebSocketDispatcher webSocketDispatcher;

    /**
     * 这个就是握手阶段
     * 
     * @param session
     * @throws Exception
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("=== 客户WebSocket连接建立开始 ===");
        log.info("Session ID: {}", session.getId());
        log.info("Remote Address: {}", session.getRemoteAddress());
        log.info("Handshake Headers: {}", session.getHandshakeHeaders());
        log.info("客户WebSocket连接建立: {}", session.getId());

        // 网关已经完成认证并透传用户信息，直接从握手头获取
        String userId = session.getHandshakeHeaders().getFirst("X-User-Id");
        String userType = session.getHandshakeHeaders().getFirst("X-User-Type");

        if (userId == null || userType == null) {
            log.warn("客户WebSocket连接缺少用户信息，关闭连接");
            session.close(CloseStatus.BAD_DATA.withReason("Missing user info"));
            return;
        }

        // 验证用户类型
        if (!"user".equals(userType)) {
            log.warn("客户WebSocket连接用户类型错误: {}, 关闭连接", userType);
            session.close(CloseStatus.BAD_DATA.withReason("Invalid user type for customer service"));
            return;
        }

        // 获取客户端IP
        String clientIp = connectionManager.getClientIp(session);

        // 检查连接限制（只记录，不拒绝连接）
        WebSocketConnectionManager.ConnectionCheckResult connectionResult = connectionManager.checkConnection(userId,
                clientIp);
        if (!connectionResult.isAllowed()) {
            log.warn("客户WebSocket连接超限但允许连接: {}, 原因: {}", userId, connectionResult.getReason());
            // 将连接限制信息存储到session中，用于后续消息发送时的限制
            session.getAttributes().put("connectionLimited", true);
            session.getAttributes().put("connectionLimitReason", connectionResult.getReason());
        }

        // 注册连接
        connectionManager.registerConnection(session.getId(), userId, clientIp);

        // 创建用户信息对象
        WebSocketUserInfo userInfo = new WebSocketUserInfo(userId, userType);
        log.info("客户WebSocket连接用户信息 - 用户ID: {}, 用户类型: {}", userId, userType);

        // 存储客户会话
        SessionManager.addCustomerSession(userId, session);
        log.info("客户连接: {}", userId);

        // 将用户信息存储到session属性中
        session.getAttributes().put("userInfo", userInfo);

        // 分布式会话管理已集成到WebSocketConnectionManager中，无需额外处理
    }

    /**
     * 这个是数据帧阶段
     * 
     * @param session
     * @param message
     * @throws Exception
     */
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // 暂时只处理文本消息
        if (message instanceof TextMessage) {
            String payload = ((TextMessage) message).getPayload();
            log.info("收到客户消息: {}", payload);
            // 解析为 WebSocketRequest
            @SuppressWarnings("unchecked")
            WebSocketDataWrapper<Object> request = (WebSocketDataWrapper<Object>) objectMapper.readValue(payload,WebSocketDataWrapper.class);
            // 使用新的Dispatcher处理消息（类似Spring MVC）
            webSocketDispatcher.dispatch(request, session);

        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("客户WebSocket传输错误", exception);
        SessionManager.removeCustomerSession(session);

        // 分布式会话管理已集成到WebSocketConnectionManager中，无需额外处理
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        log.info("客户WebSocket连接关闭: {}, 状态: {}", session.getId(), closeStatus);

        // 获取用户信息并注销连接
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo != null) {
            String clientIp = connectionManager.getClientIp(session);
            connectionManager.unregisterConnection(session.getId(), userInfo.getUserId(), clientIp);
        }

        SessionManager.removeCustomerSession(session);

        // 分布式会话管理已集成到WebSocketConnectionManager中，无需额外处理
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}
