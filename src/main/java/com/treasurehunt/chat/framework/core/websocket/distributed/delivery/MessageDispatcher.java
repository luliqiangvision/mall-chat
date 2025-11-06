package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import java.util.List;
import java.util.Set;

/**
 * 统一消息分发接口
 * 
 * 职责：
 * - 统一单聊和群聊的消息分发逻辑
 * - 根据业务规则自动判断使用哪种推送方式
 * - 为业务层提供简洁的调用接口
 * 
 * @author gaga
 * @since 2025-10-06
 */
public interface MessageDispatcher {
    
    /**
     * 统一的消息分发接口
     * 
     * 内部会根据群聊人数自动判断：
     * - 单聊（人类客服数量 <= 1）：使用点对点推送 + 重试机制
     * - 群聊（人类客服数量 > 1）：使用 Redis Stream 广播
     * 
     * @param conversationId 会话ID
     * @param serverMsgId 消息ID
     * @param senderId 发送者ID
     */
    void dispatch(String conversationId, long serverMsgId, String senderId, Set<String> memberIds);
    
    /**
     * 单聊消息推送（明确指定）
     * 
     * @param conversationId 会话ID
     * @param serverMsgId 消息ID
     * @param senderId 发送者ID
     * @param targetUserId 目标用户ID
     */
    void dispatchSingleChat(String conversationId, long serverMsgId, String senderId, String targetUserId);
    
    /**
     * 群聊消息广播（明确指定）
     * 
     * @param conversationId 会话ID
     * @param serverMsgId 消息ID
     * @param senderId 发送者ID
     * @param memberIds 目标用户列表
     */
    void dispatchGroupChat(String conversationId, long serverMsgId, String senderId, Set<String> memberIds);
}
