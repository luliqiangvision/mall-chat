package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 通知分发器（重构版）
 * 
 * 职责：
 * - 使用统一的消息分发接口
 * - 支持单聊和群聊的混合推送
 * - 为业务层提供简洁的调用接口
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class NotificationDispatcher {

	@Autowired
	private MessageDispatcher messageDispatcher;

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
	 * @param memberIds 成员ID集合（发送前会自动剔除发送者自己）
	 */
	public void dispatch(String conversationId, long serverMsgId, String senderId, Set<String> memberIds) {
		try {
			// 剔除发送者自己
			Set<String> targetMemberIds = filterSenderId(memberIds, senderId);
			if (targetMemberIds.isEmpty()) {
				log.debug("No target members after filtering sender: conversationId={}, senderId={}", conversationId, senderId);
				return;
			}
			messageDispatcher.dispatch(conversationId, serverMsgId, senderId, targetMemberIds);
			log.debug("Message dispatched: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, targetMemberIds);
		} catch (Exception e) {
			log.error("Failed to dispatch message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, memberIds, e);
		}
	}

	/**
	 * 单聊消息推送（明确指定）
	 * 
	 * @param conversationId 会话ID
	 * @param serverMsgId 消息ID
	 * @param senderId 发送者ID
	 * @param targetUserId 目标用户ID（如果与发送者相同则不发送）
	 */
	public void dispatchSingleChat(String conversationId, long serverMsgId, String senderId, String targetUserId) {
		try {
			// 如果是给自己发送，则不发送
			if (senderId != null && senderId.equals(targetUserId)) {
				log.debug("Skip sending message to self: conversationId={}, senderId={}", 
					conversationId, senderId);
				return;
			}
			messageDispatcher.dispatchSingleChat(conversationId, serverMsgId, senderId, targetUserId);
			log.debug("Single chat message dispatched: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, targetUserId);
		} catch (Exception e) {
			log.error("Failed to dispatch single chat message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, targetUserId, e);
		}
	}

	/**
	 * 群聊消息广播（明确指定）
	 * 
	 * @param conversationId 会话ID
	 * @param serverMsgId 消息ID
	 * @param senderId 发送者ID
	 * @param memberIds 成员ID集合（发送前会自动剔除发送者自己）
	 */
	public void dispatchGroupChat(String conversationId, long serverMsgId, String senderId, Set<String> memberIds) {
		try {
			// 剔除发送者自己
			Set<String> targetMemberIds = filterSenderId(memberIds, senderId);
			if (targetMemberIds.isEmpty()) {
				log.debug("dispatchGroupChat, No target members after filtering sender: conversationId={}, senderId={}", conversationId, senderId);
				return;
			}
			messageDispatcher.dispatchGroupChat(conversationId, serverMsgId, senderId, targetMemberIds);
			log.debug("Group chat message dispatched: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, targetMemberIds);
		} catch (Exception e) {
			log.error("Failed to dispatch group chat message: conversationId={}, serverMsgId={}, senderId={}, to={}", 
				conversationId, serverMsgId, senderId, memberIds, e);
		}
	}

	/**
	 * 从成员ID集合中剔除发送者ID
	 * 
	 * @param memberIds 成员ID集合
	 * @param senderId 发送者ID
	 * @return 过滤后的成员ID集合（不包含发送者）
	 */
	private Set<String> filterSenderId(Set<String> memberIds, String senderId) {
		if (memberIds == null || memberIds.isEmpty()) {
			return new HashSet<>();
		}
		if (senderId == null || senderId.isEmpty()) {
			return new HashSet<>(memberIds);
		}
		Set<String> filtered = new HashSet<>(memberIds);
		filtered.remove(senderId);
		// 剔除机器人,因为机器人不会在网页使用websocket连接,它存活在服务器内部,对他发送消息是直接的service方法调用的
		filtered.remove("666666");
		return filtered;
	}
}
