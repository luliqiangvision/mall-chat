package com.treasurehunt.chat.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 客服登录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentLoginResponse {
    
    /**
     * 访问令牌
     */
    private String token;
    
    /**
     * 客服ID
     */
    private String agentId;
    
    /**
     * 客服姓名
     */
    private String agentName;
    
    /**
     * 客服类型
     */
    private String agentType;
    
    /**
     * 租户ID
     */
    private Long tenantId;
}

