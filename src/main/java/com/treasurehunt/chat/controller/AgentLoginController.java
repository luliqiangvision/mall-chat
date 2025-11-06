package com.treasurehunt.chat.controller;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.treasurehuntshop.mall.common.api.commonUtil.CommonResult;
import com.treasurehunt.chat.domain.ChatAgentDO;
import com.treasurehunt.chat.service.AgentLoginService;
import com.treasurehunt.chat.vo.AgentLoginRequest;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客服登录控制器
 * 负责客服的登录认证相关接口
 */
@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentLoginController {

    @Autowired
    private AgentLoginService agentLoginService;
    
    @Value("${sa-token.token-prefix:Bearer}")
    private String tokenHead;

    /**
     * 客服登录
     * 参考mall-admin项目的实现方式
     * 
     * @param request 登录请求
     * @return 登录响应，包含token和tokenHead
     */
    @Operation(summary = "登录以后返回token")
    @PostMapping("/login")
    public CommonResult<Map<String, String>> login(@Validated @RequestBody AgentLoginRequest request) {
        log.info("处理客服登录请求: agentName={}", request.getAgentName());
        try {
            SaTokenInfo saTokenInfo = agentLoginService.login(request);
            if (saTokenInfo == null) {
                return CommonResult.validateFailed("用户名或密码错误");
            }
            Map<String, String> tokenMap = new HashMap<>();
            tokenMap.put("token", saTokenInfo.getTokenValue());
            tokenMap.put("tokenHead", tokenHead + " ");
            return CommonResult.success(tokenMap);
        } catch (Exception e) {
            log.error("处理客服登录失败: agentName={}", request.getAgentName(), e);
            return CommonResult.failed(e.getMessage());
        }
    }

    /**
     * 客服登出
     * 参考mall-admin项目的实现方式
     * 
     * @return 登出结果
     */
    @Operation(summary = "登出功能")
    @PostMapping("/logout")
    @ResponseBody
    public CommonResult<?> logout() {
        log.info("处理客服登出请求");
        
        try {
            agentLoginService.logout();
            return CommonResult.success(null);
        } catch (Exception e) {
            log.error("处理客服登出失败", e);
            return CommonResult.failed("登出失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前登录客服信息
     * 参考mall-admin项目的实现方式
     * 
     * @return 客服信息
     */
    @Operation(summary = "获取当前登录用户信息")
    @RequestMapping(value = "/info", method = RequestMethod.GET)
    @ResponseBody
    public CommonResult<Map<String, Object>> getAgentInfo() {
        try {
            ChatAgentDO agent = agentLoginService.getCurrentAgent();
            Map<String, Object> data = new HashMap<>();
            // 客服ID
            data.put("agentId", agent.getAgentId());
            // 对应mall-admin的username字段
            data.put("agentName", agent.getAgentName());
            // 对应mall-admin的icon字段，如果客服没有icon字段，可以设置为null或空字符串
            data.put("icon", null);
            data.put("agentType", agent.getAgentType());
            // 对应mall-admin的menus字段，如果客服没有菜单概念，返回空列表
            data.put("menus", new ArrayList<>());
            // 对应mall-admin的roles字段，使用agentType作为角色
            List<String> roles = new ArrayList<>();
            if (agent.getAgentType() != null) {
                roles.add(agent.getAgentType());
            }
            data.put("roles", roles);
            
            return CommonResult.success(data);
        } catch (Exception e) {
            log.error("获取当前登录客服信息失败", e);
            return CommonResult.failed("获取客服信息失败: " + e.getMessage());
        }
    }
}

