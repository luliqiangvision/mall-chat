package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 更新已读状态请求
 * 对应WebSocketEnvelope中interface="/updateHasRead"时的payload内容
 */
@Data
public class UpdateHasReadRequest {
    
    /**
     * 客户端消息ID（server_msg_id）
     */
    private Long clientMsgId;
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 时间戳
     */
    private Long timestamp;
}