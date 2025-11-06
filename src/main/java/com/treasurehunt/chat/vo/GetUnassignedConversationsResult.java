package com.treasurehunt.chat.vo;

import lombok.Data;
import lombok.Builder;
import java.util.List;

/**
 * 获取待分配会话列表结果
 */
@Data
@Builder
public class GetUnassignedConversationsResult {
    
    /**
     * 是否成功
     */
    private Boolean success;
    
    /**
     * 错误信息（失败时）
     */
    private String errorMessage;
    
    /**
     * 待分配的会话列表
     */
    private List<ConversationInfo> conversations;
}
