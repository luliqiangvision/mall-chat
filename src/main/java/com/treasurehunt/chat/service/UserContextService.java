package com.treasurehunt.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.vo.ReplySendMessageResult;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class UserContextService {

    @Autowired
    private ChatConversationMapper conversationMapper;

    public WebSocketUserInfo getUserInfo(WebSocketSession session) {
        Object val = session.getAttributes().get("userInfo");
        if (val instanceof WebSocketUserInfo) {
            return (WebSocketUserInfo) val;
        }
        return null;
    }

    public ReplySendMessageResult buildMissingUserResponse(ChatMessage chatMessage) {
        ReplySendMessageResult result = ReplySendMessageResult.builder()
            .clientMsgId(chatMessage.getClientMsgId())
            .conversationId(chatMessage.getConversationId())
            .timestamp(System.currentTimeMillis())
            .serverMsgId(null)
            .uiState("FAILED")
            .build();

        return result;
    }

    /**
     * 解析消息落库所需业务线：已有会话优先 {@code chat_conversation.business_line}，否则握手 {@code WebSocketUserInfo.businessLine}。
     */
    public String resolveBusinessLineForMessage(WebSocketSession session, String conversationId) {
        if (hasText(conversationId)) {
            QueryWrapper<ChatConversationDO> query = new QueryWrapper<>();
            query.eq("conversation_id", conversationId);
            ChatConversationDO conversation = conversationMapper.selectOne(query);
            if (conversation != null && hasText(conversation.getBusinessLine())) {
                return conversation.getBusinessLine().trim();
            }
        }
        WebSocketUserInfo userInfo = getUserInfo(session);
        if (userInfo != null && hasText(userInfo.getBusinessLine())) {
            return userInfo.getBusinessLine().trim();
        }
        return null;
    }

    /**
     * 落库前写入并校验业务线；缺失则拒绝保存消息。
     */
    public void applyBusinessLineForPersist(WebSocketSession session, ChatMessage chatMessage) {
        if (chatMessage == null) {
            throw new RuntimeException("缺少业务线，消息无法保存");
        }
        String resolved = firstNonBlank(
                resolveBusinessLineForMessage(session, chatMessage.getConversationId()),
                chatMessage.getBusinessLine());
        if (!hasText(resolved)) {
            throw new RuntimeException("缺少业务线（须由网关透传 X-Business-Line），消息无法保存");
        }
        chatMessage.setBusinessLine(resolved.trim());
    }

    /**
     * 统一归一化发送者身份字段：
     * ID字段：fromUserId、senderId 二者任一可用时互相补齐；
     * NO字段：fromUserNo、senderNo、userNo 三者任一可用时互相补齐；
     * 若都缺失，则使用 session 中的登录用户兜底。
     */
    public void normalizeSenderIdentity(ChatMessage chatMessage, String sessionUserId) {
        if (chatMessage == null) {
            return;
        }

        String resolvedUserId = firstNonBlank(
                chatMessage.getFromUserId(),
                chatMessage.getSenderId(),
                sessionUserId);

        String resolvedUserNo = firstNonBlank(
                chatMessage.getFromUserNo(),
                chatMessage.getSenderNo(),
                chatMessage.getUserNo());

        if (!hasText(resolvedUserId) && !hasText(resolvedUserNo)) {
            return;
        }

        if (hasText(resolvedUserId) && !hasText(chatMessage.getFromUserId())) {
            chatMessage.setFromUserId(resolvedUserId);
        }
        if (hasText(resolvedUserId) && !hasText(chatMessage.getSenderId())) {
            chatMessage.setSenderId(resolvedUserId);
        }
        if (hasText(resolvedUserNo) && !hasText(chatMessage.getFromUserNo())) {
            chatMessage.setFromUserNo(resolvedUserNo);
        }
        if (hasText(resolvedUserNo) && !hasText(chatMessage.getSenderNo())) {
            chatMessage.setSenderNo(resolvedUserNo);
        }
        if (hasText(resolvedUserNo) && !hasText(chatMessage.getUserNo())) {
            chatMessage.setUserNo(resolvedUserNo);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
