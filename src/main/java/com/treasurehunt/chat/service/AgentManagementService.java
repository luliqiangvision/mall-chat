package com.treasurehunt.chat.service;

import com.treasurehunt.chat.domain.ChatAgentDO;
import com.treasurehunt.chat.mapper.ChatAgentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客服管理服务
 * 负责客服信息的查询和管理
 */
@Slf4j
@Service
public class AgentManagementService {
    
    @Autowired
    private ChatAgentMapper chatAgentMapper;
    
    /**
     * 获取售前客服ID列表
     * 
     * @param tenantId 租户ID
     * @return 售前客服ID集合
     */
    public Set<String> getPreSalesAgentIds(Long tenantId) {
        try {
            List<ChatAgentDO> agents = chatAgentMapper.selectPreSalesAgents(tenantId);
            Set<String> agentIds = agents.stream()
                    .map(ChatAgentDO::getAgentId)
                    .collect(Collectors.toSet());
            
            log.debug("查询售前客服: tenantId={}, agentIds={}", tenantId, agentIds);
            return agentIds;
            
        } catch (Exception e) {
            log.error("查询售前客服失败: tenantId={}", tenantId, e);
            return Collections.emptySet(); // 返回空集合
        }
    }
    
    /**
     * 获取售后客服ID列表
     * 
     * @param tenantId 租户ID
     * @return 售后客服ID集合
     */
    public Set<String> getAfterSalesAgentIds(Long tenantId) {
        try {
            List<ChatAgentDO> agents = chatAgentMapper.selectAfterSalesAgents(tenantId);
            Set<String> agentIds = agents.stream()
                    .map(ChatAgentDO::getAgentId)
                    .collect(Collectors.toSet());
            
            log.debug("查询售后客服: tenantId={}, agentIds={}", tenantId, agentIds);
            return agentIds;
            
        } catch (Exception e) {
            log.error("查询售后客服失败: tenantId={}", tenantId, e);
            return Collections.emptySet(); // 返回空集合
        }
    }
    
    /**
     * 根据客服类型获取客服ID列表
     * 
     * @param agentType 客服类型
     * @param tenantId 租户ID
     * @return 客服ID集合
     */
    public Set<String> getAgentIdsByType(String agentType, Long tenantId) {
        try {
            List<ChatAgentDO> agents = chatAgentMapper.selectByTypeAndStatus(agentType, "active", tenantId);
            Set<String> agentIds = agents.stream()
                    .map(ChatAgentDO::getAgentId)
                    .collect(Collectors.toSet());
            
            log.debug("查询客服: agentType={}, tenantId={}, agentIds={}", agentType, tenantId, agentIds);
            return agentIds;
            
        } catch (Exception e) {
            log.error("查询客服失败: agentType={}, tenantId={}", agentType, tenantId, e);
            return Collections.emptySet(); // 返回空集合
        }
    }
    
    /**
     * 获取负载最低的售前客服
     * 
     * @param tenantId 租户ID
     * @return 负载最低的售前客服ID，如果没有则返回null
     */
    public String getLeastLoadedPreSalesAgent(Long tenantId) {
        try {
            ChatAgentDO agent = chatAgentMapper.selectLeastLoadedPreSalesAgent(tenantId);
            if (agent != null) {
                log.debug("查询负载最低的售前客服: tenantId={}, agentId={}, currentConversations={}", 
                        tenantId, agent.getAgentId(), agent.getCurrentConversations());
                return agent.getAgentId();
            } else {
                log.warn("没有可用的售前客服: tenantId={}", tenantId);
                return null;
            }
            
        } catch (Exception e) {
            log.error("查询负载最低的售前客服失败: tenantId={}", tenantId, e);
            return null;
        }
    }
    
    /**
     * 检查客服是否存在且活跃
     * 
     * @param agentId 客服ID
     * @return 是否存在且活跃
     */
    public boolean isAgentActive(String agentId) {
        try {
            ChatAgentDO agent = chatAgentMapper.selectByAgentId(agentId);
            return agent != null && "active".equals(agent.getStatus());
            
        } catch (Exception e) {
            log.error("检查客服状态失败: agentId={}", agentId, e);
            return false;
        }
    }
    
    /**
     * 更新客服当前会话数
     * 
     * @param agentId 客服ID
     * @param currentConversations 当前会话数
     */
    public void updateAgentCurrentConversations(String agentId, Integer currentConversations) {
        try {
            int updated = chatAgentMapper.updateCurrentConversations(agentId, currentConversations);
            if (updated > 0) {
                log.debug("更新客服会话数: agentId={}, currentConversations={}", agentId, currentConversations);
            } else {
                log.warn("更新客服会话数失败，客服不存在: agentId={}", agentId);
            }
            
        } catch (Exception e) {
            log.error("更新客服会话数失败: agentId={}, currentConversations={}", agentId, currentConversations, e);
        }
    }
    
    /**
     * 更新客服状态
     * 
     * @param agentId 客服ID
     * @param status 客服状态
     */
    public void updateAgentStatus(String agentId, String status) {
        try {
            int updated = chatAgentMapper.updateAgentStatus(agentId, status);
            if (updated > 0) {
                log.info("更新客服状态: agentId={}, status={}", agentId, status);
            } else {
                log.warn("更新客服状态失败，客服不存在: agentId={}", agentId);
            }
            
        } catch (Exception e) {
            log.error("更新客服状态失败: agentId={}, status={}", agentId, status, e);
        }
    }
}
