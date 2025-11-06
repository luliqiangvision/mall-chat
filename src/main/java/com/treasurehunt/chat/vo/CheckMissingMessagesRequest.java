package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 检查缺失消息请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckMissingMessagesRequest {
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 范围查询起始serverMsgId（不包含）
     */
    private Long startServerMsgId;
    
    /**
     * 范围查询结束serverMsgId（不包含）
     */
    private Long endServerMsgId;
}
