package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 通知拉取Payload
 * 对应WebSocketEnvelope中interface="notifyPull"时的payload内容
 */
@Data
public class NotifyPullPayload {
    
    /**
     * 会话ID
     */
    private String conversationId;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 服务器消息ID
     */
    private Long serverMsgId;
    
    /**
     * 消息状态
     */
    private String status;
}
