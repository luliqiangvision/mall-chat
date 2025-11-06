package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.Set;

/**
 * 单聊推送器
 * 
 * 职责：
 * - 处理单聊消息的点对点推送
 * - 使用现有的重试机制和连接池
 * - 保证消息的可靠送达
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class SingleChatPusher {

    @Autowired
    private UserSessionMetadataManager userSessionMetadataManager;

    @Autowired
    private RetryManager retryManager;

    // 配置参数
    @Value("${spring.application.name}")
    private String applicationName;

    /**
     * 推送单聊消息
     * 
     * @param conversationId 会话ID
     * @param serverMsgId    消息ID
     * @param senderId       发送者ID
     * @param targetUserId   目标用户ID（由业务层传入）
     */
    public void push(String conversationId, long serverMsgId, String senderId, String targetUserId) {
        try {
            if (targetUserId == null) {
                log.warn("Target user ID is null for single chat: conversationId={}, senderId={}",
                        conversationId, senderId);
                return;
            }

            // 1. 获取目标实例地址
            String targetInstanceAddress = userSessionMetadataManager.getInstanceAddress(targetUserId);
            if (targetInstanceAddress == null) {
                log.debug("Target user {} is offline, message will be stored in DB", targetUserId);
                return;
            }

            // 2. 创建推送消息
            NotificationMessage message = new NotificationMessage(
                    applicationName, //使用Spring应用名作为服务类型
                    conversationId,
                    serverMsgId,
                    senderId, // 发送者
                    Set.of(targetUserId) // 目标用户列表
            );

            // 3. 使用重试机制推送
            retryManager.executeWithRetry(targetUserId, message, targetInstanceAddress);

            log.debug("Single chat message pushed: conversationId={}, serverMsgId={}, senderId={}, targetUserId={}",
                    conversationId, serverMsgId, senderId, targetUserId);

        } catch (Exception e) {
            log.error("Failed to push single chat message: conversationId={}, serverMsgId={}, senderId={}",
                    conversationId, serverMsgId, senderId, e);
        }
    }
}
