package com.treasurehunt.chat.framework.core.websocket.distributed.controller;

import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotifyPushSender;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.UserSessionMetadataManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

/**
 * HTTP/2服务器间通信控制器
 * 
 * 用于HTTP/2协议接收来自其他实例的推送消息
 * 当使用HTTP/2协议时，其他实例会通过HTTP POST请求推送消息到此端点
 * 
 * 框架层组件，不依赖业务层
 * 
 * @author gaga
 * @since 2025-01-24
 */
@Slf4j
@RestController
@RequestMapping("/server")
@ConditionalOnProperty(name = "websocket.distributed.server-comm.protocol", havingValue = "http2")
public class Http2ServerCommController {
    
    @Autowired
    private NotifyPushSender notifyPushSender;
    
    @Autowired
    private UserSessionMetadataManager userSessionMetadataManager;
    
    /**
     * 接收来自其他实例的推送消息
     * 
     * @param message 推送消息
     * @return 处理结果
     */
    @PostMapping("/push")
    public Map<String, Object> receivePush(@RequestBody Map<String, Object> message) {
        try {
            log.debug("Received server push message: {}", message);
            
            // 解析消息
            String conversationId = (String) message.get("conversationId");
            Object serverMsgIdObj = message.get("serverMsgId");
            Long serverMsgId = null;
            
            if (serverMsgIdObj instanceof Number) {
                serverMsgId = ((Number) serverMsgIdObj).longValue();
            }
            
            if (conversationId == null || serverMsgId == null) {
                log.warn("Invalid push message format: {}", message);
                return Map.of("success", false, "error", "Invalid message format");
            }
            
            // 获取目标用户列表
            @SuppressWarnings("unchecked")
            Set<String> targetUserIds = (Set<String>) message.get("targetUserIds");
            
            if (targetUserIds != null) {
                for (String userId : targetUserIds) {
                    // 获取用户在本机的所有会话ID
                    Set<String> sessionIds = userSessionMetadataManager.getSessionIdsByUserId(userId);
                    if (sessionIds != null && !sessionIds.isEmpty()) {
                        // 向每个会话推送通知
                        for (String sessionId : sessionIds) {
                            try {
                                notifyPushSender.sendNotifyPullLocal(sessionId, conversationId, serverMsgId);
                                log.debug("HTTP/2 relay: pushed to user={}, sessionId={}, conversationId={}", 
                                    userId, sessionId, conversationId);
                            } catch (Exception e) {
                                log.error("HTTP/2 relay: failed to push to user={}, sessionId={}", userId, sessionId, e);
                            }
                        }
                    }
                }
            }
            
            return Map.of("success", true, "message", "Push processed successfully");
            
        } catch (Exception e) {
            log.error("Failed to process server push message", e);
            return Map.of("success", false, "error", e.getMessage());
        }
    }
}
