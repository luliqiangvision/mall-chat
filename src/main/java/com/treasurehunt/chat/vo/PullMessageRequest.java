package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拉取消息请求
 * 对应WebSocketEnvelope中interface="pullMessage"时的payload内容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PullMessageRequest {

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 消息类型
     */
    private String type;

    /**
     * 时间戳
     */
    private Long timestamp;

    /**
     * 要拉取的信息的 serverMsgId
     */
    private Long serverMsgId;

    /**
     * 消息列表 (服务端响应时提供)
     */
    private ChatMessage message;
}
