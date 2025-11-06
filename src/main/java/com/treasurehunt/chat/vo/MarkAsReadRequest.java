package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 标记已读请求（HTTP接口）
 */
@Data
public class MarkAsReadRequest {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 消息ID（serverMsgId）
     */
    private Long serverMsgId;
}

