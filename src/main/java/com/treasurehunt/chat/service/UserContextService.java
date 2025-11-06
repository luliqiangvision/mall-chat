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
}


