package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重连校验请求：用于查询会话中大于指定 serverMsgId 的缺失消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckMessageRequest {

    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 客户端已知的最大 server_msg_id（lastServerMsgId）
     */
    private Long serverMsgId;
}


