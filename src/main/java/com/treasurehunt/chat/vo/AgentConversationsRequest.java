package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 客服获取会话列表请求
 */
@Data
public class AgentConversationsRequest {
    
    /**
     * 客服ID
     */
    private String agentId;

    /**
     * 用户编号冗余字段
     */
    private String userno;
}
