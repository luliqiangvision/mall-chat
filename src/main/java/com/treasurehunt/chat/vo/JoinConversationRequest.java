package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;

/**
 * 加入会话请求
 * 对应WebSocketEnvelope中interface="joinConversation"时的payload内容
 */
@Data
@Builder
public class JoinConversationRequest {
    
    /**
     * 客服ID
     */
    private String agentId;
    
    /**
     * 要加入的会话ID
     */
    private String conversationId;
}
