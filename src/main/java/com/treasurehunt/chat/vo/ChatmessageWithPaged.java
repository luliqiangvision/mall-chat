package com.treasurehunt.chat.vo;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分页拉取消息请求
 * 对应WebSocketEnvelope中interface="pullMessageWithPagedQuery"时的payload内容
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatmessageWithPaged {
    /**
     * 会话ID
     */
    private String conversationId;

    /**
     * 页码 (1代表倒数第一页)
     */
    private Integer currentPage;
    /**
     * 消息列表
     */
    private List<ChatMessage> chatMessages;
}
