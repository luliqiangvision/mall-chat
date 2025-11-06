package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 客服删除会话请求
 */
@Data
public class AgentDeleteConversationRequest {
    
    /**
     * 客服ID
     */
    private String agentId;
    
    /**
     * 会话ID
     */
    private String conversationId;
}
