package com.treasurehunt.chat.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import com.treasurehunt.chat.domain.ChatAgentDO;
import com.treasurehunt.chat.mapper.ChatAgentMapper;
import com.treasurehunt.chat.utils.StpUtilForType;
import com.treasurehunt.chat.vo.AgentLoginRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 客服登录服务
 * 负责客服的登录认证和token生成
 */
@Slf4j
@Service
public class AgentLoginService {

    @Autowired
    private ChatAgentMapper chatAgentMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 客服登录
     * 参考mall-admin项目的实现方式
     * 
     * @param request 登录请求
     * @return SaTokenInfo token信息
     */
    public SaTokenInfo login(AgentLoginRequest request) {
        log.info("客服登录请求: agentName={}", request.getAgentName());

        // 1. 参数校验
        if (request.getAgentName() == null || request.getAgentName().isEmpty() ||
            request.getPassword() == null || request.getPassword().isEmpty()) {
            throw new RuntimeException("用户名或密码不能为空！");
        }

        // 2. 根据客服名称查询客服信息
        ChatAgentDO agent = chatAgentMapper.selectByAgentName(request.getAgentName());
        if (agent == null) {
            log.warn("客服不存在: agentName={}", request.getAgentName());
            throw new RuntimeException("客服名称或密码错误");
        }

        // 3. 验证密码
        if (!passwordEncoder.matches(request.getPassword(), agent.getPassword())) {
            log.warn("密码错误: agentName={}", request.getAgentName());
            throw new RuntimeException("客服名称或密码错误");
        }

        // 4. 检查客服状态
        if (!"active".equals(agent.getStatus())) {
            log.warn("客服状态异常: agentName={}, status={}", request.getAgentName(), agent.getStatus());
            throw new RuntimeException("客服账号已被禁用");
        }

        // 5. 登录校验成功后，调用登录方法
        StpUtilForType.login(StpUtilForType.TYPE_AGENT_LOGIN, agent.getAgentId());
        
        // 6. 获取当前登录用户Token信息
        SaTokenInfo saTokenInfo = StpUtilForType.getTokenInfo(StpUtilForType.TYPE_AGENT_LOGIN);
        
        log.info("客服登录成功: agentName={}, token={}", request.getAgentName(), saTokenInfo.getTokenValue());

        return saTokenInfo;
    }

    /**
     * 客服登出
     * 参考mall-admin项目的实现方式，直接调用logout方法
     */
    public void logout() {
        try {
            // 获取当前登录的agentId用于日志记录
            if (StpUtilForType.isLogin(StpUtilForType.TYPE_AGENT_LOGIN)) {
                String agentId = StpUtilForType.getLoginIdAsString(StpUtilForType.TYPE_AGENT_LOGIN);
                log.info("客服登出: agentId={}", agentId);
            }
            // 调用sa-token的登出方法
            StpUtilForType.logout(StpUtilForType.TYPE_AGENT_LOGIN);
        } catch (Exception e) {
            log.warn("登出时发生异常，尝试直接退出当前会话: {}", e.getMessage());
            // 即使出现异常，也尝试退出当前会话
            try {
                StpUtilForType.logout(StpUtilForType.TYPE_AGENT_LOGIN);
            } catch (Exception ex) {
                log.warn("直接退出会话失败，可能token已失效: {}", ex.getMessage());
                // 即使失败也不抛出异常，避免影响用户体验
            }
        }
    }

    /**
     * 获取当前登录的客服信息
     * 
     * @return 客服信息
     */
    public ChatAgentDO getCurrentAgent() {
        // 从StpUtilForType获取当前登录的客服ID
        String agentId = StpUtilForType.getLoginIdAsString(StpUtilForType.TYPE_AGENT_LOGIN);
        
        // 根据客服ID查询客服信息
        ChatAgentDO agent = chatAgentMapper.selectByAgentId(agentId);
        if (agent == null) {
            log.warn("当前登录的客服不存在: agentId={}", agentId);
            throw new RuntimeException("当前登录的客服不存在");
        }
        
        return agent;
    }
}

