package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 初始化会话视图请求
 */
@Data
public class InitConversationViewRequest {
    
    /**
     * 用户ID（客户或客服）
     */
    private String userId;
}
