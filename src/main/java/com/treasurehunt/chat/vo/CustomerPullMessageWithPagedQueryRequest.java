package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客户分页拉取消息请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerPullMessageWithPagedQueryRequest {
    /**
     * 会话ID
     */
    private String conversationId;
    /**
     * 一次拉取几条
     */
    private Integer pageSize;
    /**
     * 页码 (1代表倒数第一页)
     */
    private Integer currentPage;
}
