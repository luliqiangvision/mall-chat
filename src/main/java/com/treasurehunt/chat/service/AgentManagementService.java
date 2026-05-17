package com.treasurehunt.chat.service;

import com.treasurehunt.chat.domain.ChatAgentDO;
import com.treasurehunt.chat.enums.AgentTypeEnum;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.UserSessionMetadataManager;
import com.treasurehunt.chat.mapper.ChatAgentMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客服管理服务
 * 负责客服信息的查询和管理
 */
@Slf4j
@Service
public class AgentManagementService {

    private static final String AGENT_STATUS_ACTIVE = "active";

    @Autowired
    private ChatAgentMapper chatAgentMapper;

    @Autowired
    private UserSessionMetadataManager userSessionMetadataManager;

    /**
     * 店铺绑定池内售前客服 ID（单次 {@link ChatAgentMapper#selectByBusinessLineShopTypeAndStatus}，不再按租户反查店铺列表）。
     * 建议 {@code agentType} 使用 {@link AgentTypeEnum#PRE_SALES}{@code .getCode()}。
     */
    public Set<String> getPreSalesAgentIds(String businessLine, Long shopId, String agentType, String status) {
        if (businessLine == null || businessLine.isEmpty() || shopId == null
                || agentType == null || agentType.isEmpty() || status == null || status.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<ChatAgentDO> agents = chatAgentMapper.selectByBusinessLineShopTypeAndStatus(
                    businessLine.trim(), shopId, agentType, status);
            Set<String> agentIds = agents.stream().map(ChatAgentDO::getAgentId).collect(Collectors.toSet());
            log.debug("查询售前客服(店铺池): businessLine={}, shopId={}, agentType={}, status={}, agentIds={}",
                    businessLine, shopId, agentType, status, agentIds);
            return agentIds;
        } catch (Exception e) {
            log.error("查询售前客服(店铺池)失败: businessLine={}, shopId={}, agentType={}, status={}",
                    businessLine, shopId, agentType, status, e);
            return Collections.emptySet();
        }
    }

    /**
     * 店铺绑定池内售后客服 ID（单次 {@link ChatAgentMapper#selectByBusinessLineShopTypeAndStatus}）。
     */
    public Set<String> getAfterSalesAgentIds(String businessLine, Long shopId, String agentType, String status) {
        if (businessLine == null || businessLine.isEmpty() || shopId == null
                || agentType == null || agentType.isEmpty() || status == null || status.isEmpty()) {
            return Collections.emptySet();
        }
        try {
            List<ChatAgentDO> agents = chatAgentMapper.selectByBusinessLineShopTypeAndStatus(
                    businessLine.trim(), shopId, agentType, status);
            Set<String> agentIds = agents.stream().map(ChatAgentDO::getAgentId).collect(Collectors.toSet());
            log.debug("查询售后客服(店铺池): businessLine={}, shopId={}, agentType={}, status={}, agentIds={}",
                    businessLine, shopId, agentType, status, agentIds);
            return agentIds;
        } catch (Exception e) {
            log.error("查询售后客服(店铺池)失败: businessLine={}, shopId={}, agentType={}, status={}",
                    businessLine, shopId, agentType, status, e);
            return Collections.emptySet();
        }
    }

    /**
     * 客户进线选人：有 {@code shopId} 时仅从店铺售前池选（优先在线，均离线则选离线售前，不回退 corporate）；
     * 无 {@code shopId} 时走业务线 {@link AgentTypeEnum#CORPORATE}。不以 {@code tenant_id} 为维度。
     */
    public String resolveLeastLoadedAgentForCustomerRouting(String businessLine, Long shopId) {
        if (businessLine == null || businessLine.isEmpty()) {
            return null;
        }
        String bl = businessLine.trim();
        try {
            if (shopId != null) {
                return resolveShopPreSalesAgent(bl, shopId);
            }
            return resolveCorporateAgent(bl);
        } catch (Exception e) {
            log.error("客户进线选人失败: businessLine={}, shopId={}", bl, shopId, e);
            return null;
        }
    }

    /**
     * 有店：店铺绑定售前池；优先在线中负载最低，均离线则选离线中负载最低（不按 max 会话数排除任何人）。
     */
    private String resolveShopPreSalesAgent(String businessLine, Long shopId) {
        List<ChatAgentDO> pool = chatAgentMapper.selectByBusinessLineShopTypeAndStatus(
                businessLine, shopId, AgentTypeEnum.PRE_SALES.getCode(), AGENT_STATUS_ACTIVE);
        if (pool == null || pool.isEmpty()) {
            log.error(
                    "[SHOP_PRE_SALES_POOL_EMPTY] 店铺未配置有效售前绑定（chat_agent_shop_relation 无 business_line+shop_id 下 pre-sales 且 active 的客服），无法分配主接待: businessLine={}, shopId={}, [TODO: alerting]",
                    businessLine, shopId);
            return null;
        }

        String online = pickLeastLoaded(filterByOnline(pool, true));
        if (online != null) {
            log.debug("店铺池选中(在线): businessLine={}, shopId={}, agentId={}", businessLine, shopId, online);
            return online;
        }
        String offline = pickLeastLoaded(filterByOnline(pool, false));
        if (offline != null) {
            log.debug("店铺池选中(离线): businessLine={}, shopId={}, agentId={}", businessLine, shopId, offline);
            return offline;
        }
        log.error(
                "[SHOP_PRE_SALES_POOL_EMPTY] 店铺售前池非空但无法选出主接待: businessLine={}, shopId={}, poolSize={}, [TODO: alerting]",
                businessLine, shopId, pool.size());
        return null;
    }

    private List<ChatAgentDO> filterByOnline(List<ChatAgentDO> pool, boolean online) {
        return pool.stream()
                .filter(a -> isAgentOnline(a.getAgentId()) == online)
                .collect(Collectors.toList());
    }

    private boolean isAgentOnline(String agentId) {
        if (agentId == null || agentId.isEmpty()) {
            return false;
        }
        try {
            return userSessionMetadataManager.isUserOnline(agentId);
        } catch (Exception e) {
            log.warn("查询客服在线状态失败，视为离线: agentId={}", agentId, e);
            return false;
        }
    }

    private String pickLeastLoaded(List<ChatAgentDO> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        return candidates.stream()
                .min(Comparator.comparingInt(a -> a.getCurrentConversations() == null ? 0 : a.getCurrentConversations()))
                .map(ChatAgentDO::getAgentId)
                .orElse(null);
    }

    private String resolveCorporateAgent(String businessLine) {
        ChatAgentDO corporate = chatAgentMapper.selectLeastLoadedAgentByBusinessLineAndType(businessLine,
                AgentTypeEnum.CORPORATE.getCode());
        if (corporate != null) {
            log.debug("公司级池选中: businessLine={}, agentId={}", businessLine, corporate.getAgentId());
            return corporate.getAgentId();
        }
        log.warn("公司级池无可用客服: businessLine={}", businessLine);
        return null;
    }

    public boolean isAgentActive(String businessLine, String agentId) {
        try {
            ChatAgentDO agent = chatAgentMapper.selectByBusinessLineAndAgentId(businessLine, agentId);
            return agent != null && AGENT_STATUS_ACTIVE.equals(agent.getStatus());

        } catch (Exception e) {
            log.error("检查客服状态失败: businessLine={}, agentId={}", businessLine, agentId, e);
            return false;
        }
    }

    public void updateAgentCurrentConversations(String businessLine, String agentId, Integer currentConversations) {
        if (businessLine == null || businessLine.isEmpty() || agentId == null || agentId.isEmpty()) {
            return;
        }
        try {
            int updated = chatAgentMapper.updateCurrentConversations(businessLine.trim(), agentId, currentConversations);
            if (updated > 0) {
                log.debug("更新客服会话数: businessLine={}, agentId={}, currentConversations={}",
                        businessLine, agentId, currentConversations);
            } else {
                log.warn("更新客服会话数失败，客服不存在或业务线不匹配: businessLine={}, agentId={}", businessLine, agentId);
            }

        } catch (Exception e) {
            log.error("更新客服会话数失败: businessLine={}, agentId={}, currentConversations={}",
                    businessLine, agentId, currentConversations, e);
        }
    }

    public void updateAgentStatus(String businessLine, String agentId, String status) {
        if (businessLine == null || businessLine.isEmpty() || agentId == null || agentId.isEmpty()) {
            return;
        }
        try {
            int updated = chatAgentMapper.updateAgentStatus(businessLine.trim(), agentId, status);
            if (updated > 0) {
                log.info("更新客服状态: businessLine={}, agentId={}, status={}", businessLine, agentId, status);
            } else {
                log.warn("更新客服状态失败，客服不存在或业务线不匹配: businessLine={}, agentId={}", businessLine, agentId);
            }

        } catch (Exception e) {
            log.error("更新客服状态失败: businessLine={}, agentId={}, status={}", businessLine, agentId, status, e);
        }
    }
}
