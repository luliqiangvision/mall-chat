package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 检查缺失消息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckMissingMessagesResponse {
    /**
     * 是否有缺失消息
     */
    private boolean hasMissingMessages;
    
    /**
     * 缺失的消息列表
     */
    private List<ChatMessage> missingMessages;
    
    /**
     * 缺失消息的数量
     */
    private Integer missingCount;
    
    /**
     * 检查的会话ID
     */
    private String conversationId;
}
