package com.treasurehunt.chat.vo;

import lombok.Data;

/**
 * 客户获取会话列表请求
 */
@Data
public class CustomerConversationsRequest {
    
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户编号冗余字段
     */
    private String userno;
}
