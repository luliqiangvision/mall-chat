package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.extern.slf4j.Slf4j;

import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 统一消息分发器实现
 * 
 * 职责：
 * - 实现 MessageDispatcher 接口
 * - 根据业务规则自动判断使用单聊还是群聊推送
 * - 为业务层提供统一的调用接口
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class UnifiedMessageDispatcher implements MessageDispatcher {

    @Autowired
    private GroupChatBroadcaster groupChatBroadcaster;

    @Autowired
    private SingleChatPusher singleChatPusher;

    @Override
    public void dispatch(String conversationId, long serverMsgId, String senderId, Set<String> memberIds) {
        try {
            // 根据群聊人数自动判断使用哪种推送方式
            if (memberIds.size() > 1) {
                log.debug("Dispatching as group chat: conversationId={}, senderId={}, to={}", conversationId, senderId, memberIds);
                dispatchGroupChat(conversationId, serverMsgId, senderId, memberIds);
            } else {
                String targetUserId = memberIds.iterator().next();
                log.debug("Dispatching as single chat: conversationId={}, senderId={}, to={}", conversationId, senderId, targetUserId);
                dispatchSingleChat(conversationId, serverMsgId, senderId, targetUserId);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
                conversationId, serverMsgId, senderId, memberIds, e);
        }
    }

    @Override
    public void dispatchSingleChat(String conversationId, long serverMsgId, String senderId, String targetUserId) {
        try {
            singleChatPusher.push(conversationId, serverMsgId, senderId, targetUserId);
            log.debug("Single chat message dispatched: conversationId={}, serverMsgId={}, senderId={}, to={}", 
                conversationId, serverMsgId, senderId, targetUserId);
        } catch (Exception e) {
            log.error("Failed to dispatch single chat message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
                conversationId, serverMsgId, senderId, targetUserId, e);
        }
    }

    @Override
    public void dispatchGroupChat(String conversationId, long serverMsgId, String senderId, Set<String> memberIds) {
        try {
            groupChatBroadcaster.broadcast(conversationId, serverMsgId, senderId, memberIds);
            log.debug("Group chat message dispatched: conversationId={}, serverMsgId={}, senderId={}, to={}", 
                conversationId, serverMsgId, senderId, memberIds);
        } catch (Exception e) {
            log.error("Failed to dispatch group chat message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
                conversationId, serverMsgId, senderId, memberIds, e);
        }
    }
}
