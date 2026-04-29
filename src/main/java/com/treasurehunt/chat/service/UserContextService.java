package com.treasurehunt.chat.service;


import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.vo.ReplySendMessageResult;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class UserContextService {

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


