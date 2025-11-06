package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 检查未读消息请求
 * 对应WebSocketEnvelope中interface="/checkUnreadMessages"时的payload内容
 */
@Data
public class CheckUnreadMessagesRequest {
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 时间戳（可选，用于客户端缓存控制）
     */
    private Long timestamp;
}
