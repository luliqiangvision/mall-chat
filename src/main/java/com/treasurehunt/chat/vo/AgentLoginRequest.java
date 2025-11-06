package com.treasurehunt.chat.vo;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * 客服登录请求
 */
@Data
public class AgentLoginRequest {
    
    /**
     * 客服名称
     */
    @NotBlank(message = "客服名称不能为空")
    private String agentName;
    
    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}

