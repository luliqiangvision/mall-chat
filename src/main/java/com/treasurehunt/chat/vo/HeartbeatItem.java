package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatItem {

    /** 会话ID */
    private String conversationId;

    /** 客户端已知的会话内最大 serverMsgId */
    private Long clientMaxServerMsgId;
}


