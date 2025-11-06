package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 发送消息结果
 */
@Data
@Builder
@AllArgsConstructor
public class ReplySendMessageResult {

    /**
     * 客户端消息ID
     */
    private String clientMsgId;

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
     * UI状态
     */
    private String uiState;
}
