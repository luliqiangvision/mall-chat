package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;

/**
 * 加入会话结果
 */
@Data
@Builder
public class JoinConversationResult {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
    
    /**
     * 会话信息（成功时）
     */
    private ConversationInfo conversationInfo;
}
