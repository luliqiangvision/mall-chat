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
     * 用户编号冗余字段
     */
    private String userno;
    
    /**
     * 会话ID
     */
    private String conversationId;
}
