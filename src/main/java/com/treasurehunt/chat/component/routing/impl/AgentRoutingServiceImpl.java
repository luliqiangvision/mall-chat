package com.treasurehunt.chat.component.routing.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.mapper.ChatConversationMemberMapper;
import com.treasurehunt.chat.component.manager.ChatWindowManager;
import com.treasurehunt.chat.service.AgentManagementService;
import com.treasurehunt.chat.component.routing.AgentRoutingService;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 客服路由业务实现（群聊模式）
 * - 基于 chat_conversation_member 维护多客服成员
 * - 汇总每个 agent 的在线 sessionId 用于消息投递
 */
@Slf4j
@Service
public class AgentRoutingServiceImpl implements AgentRoutingService {

	@Autowired
	private ChatConversationMemberMapper conversationMemberMapper;

	@Autowired
	private StatefulRedisConnection<String, String> redisConnection;

	@Autowired
	private ChatWindowManager chatWindowManager;

	@Autowired
	private AgentManagementService agentManagementService;

	/**
	 * 为聊天窗口分配客服（群聊模式）,这个方法目前只做数据库层面上的绑定,缓存没有实现,以后看情况是否要实现,抄送的功能不在这里,这里只管分配
	 * 
	 * 场景1：新聊天窗口创建
	 * 聊天窗口不存在 → 分配给售前客服
	 * 同时抄送给机器人
	 * 
	 * 场景2：已存在聊天窗口，客服还在群里
	 * 聊天窗口存在，客服成员还在 → 推送给群成员
	 * 同时抄送给机器人
	 * 
	 * 场景3：已存在聊天窗口，所有客服都退群了
	 * 聊天窗口存在，但 chat_conversation_member 中没有人类客服了 → 分配给售前客服
	 * 同时抄送给机器人
	 */
	@Override
	public RouteResult assignAgents(String conversationId, boolean isNewConversation) {
		if (conversationId == null || conversationId.isEmpty()) {
			log.warn("conversationId is null/empty");
			return new RouteResult(Collections.emptySet(), false);
		}

		// 场景1：新会话创建
		if (isNewConversation) {
			return assignPreSalesAgentsWithFallback(conversationId);
		}
		
		// 场景2&3：已存在会话
		return assignExistingOrPreSalesAgents(conversationId);
	}

	/**
	 * 场景1：新聊天窗口创建 - 分配给售前客服
	 */
	private RouteResult assignPreSalesAgentsWithFallback(String conversationId) {
		log.info("场景1：新会话创建，分配给售前客服: conversationId={}", conversationId);
		
		// 1. 获取售前客服
		Set<String> preSalesAgentIds = getPreSalesAgentIds();
		// 这里是防御式编程,一般肯定是有首先客服的账号的
		if (preSalesAgentIds.isEmpty()) {
			log.warn("没有售前客服，直接给机器人: conversationId={}", conversationId);
			return new RouteResult(Collections.emptySet(), false);
		}
		
		// 2. 绑定售前客服到聊天窗口
		chatWindowManager.bindAgents(conversationId, new ArrayList<>(preSalesAgentIds));
		
		log.info("场景1：已绑定售前客服: conversationId={}, agentIds={}", conversationId, preSalesAgentIds);
		
		// 3. 返回售前客服列表（不管是否在线，都推送给售前客服，同时抄送给机器人）
		return new RouteResult(preSalesAgentIds, true);
	}

	/**
	 * 场景2&3：已存在会话 - 检查现有客服或分配新客服
	 */
	private RouteResult assignExistingOrPreSalesAgents(String conversationId) {
		// 1. 查询现有客服成员
		QueryWrapper<ChatConversationMemberDO> memberQuery = new QueryWrapper<>();
		memberQuery.eq("conversation_id", conversationId)
				   .eq("member_type", "agent")
				   .isNull("left_at");
		
		List<ChatConversationMemberDO> members = conversationMemberMapper.selectList(memberQuery);
		
		if (!members.isEmpty()) {
			// 场景2：客服还在群里，推送给群成员
			Set<String> existingAgentIds = members.stream()
					.map(ChatConversationMemberDO::getMemberId)
					.collect(Collectors.toSet());
			
			log.info("场景2：客服还在群里，推送给群成员: conversationId={}, agentIds={}", conversationId, existingAgentIds);
			
			// 推送给所有现有客服（不管是否在线，都推送给现有客服，同时抄送给机器人）
			return new RouteResult(existingAgentIds, true);
		}
		
		// 场景3：所有客服都退群了，分配给售前客服
		log.info("场景3：所有客服都退群了，分配给售前客服: conversationId={}", conversationId);
		return assignPreSalesAgentsWithFallback(conversationId);
	}

	/**
	 * 获取售前客服ID列表
	 * 从客服管理服务查询活跃的售前客服
	 */
	private Set<String> getPreSalesAgentIds() {
		try {
			// 默认使用租户ID为1，实际项目中可以从上下文获取
			Long tenantId = 1L;
			Set<String> agentIds = agentManagementService.getPreSalesAgentIds(tenantId);
			
			if (agentIds.isEmpty()) {
				log.warn("没有找到活跃的售前客服: tenantId={}", tenantId);
			} else {
				log.debug("查询到售前客服: tenantId={}, agentIds={}", tenantId, agentIds);
			}
			
			return agentIds;
			
		} catch (Exception e) {
			log.error("查询售前客服失败", e);
			return Collections.emptySet();
		}
	}


	/**
	 * 指派首个客服到会话（抢占式首绑）
	 * 仅在当前无客服时才能成功绑定
	 * 
	 * @return true 表示成功抢占，false 表示已有人占用
	 */
	@Override
	public boolean bindFirstAgent(String conversationId, String agentId) {
		if (conversationId == null || conversationId.isEmpty() || agentId == null || agentId.isEmpty()) {
			log.warn("conversationId or agentId is null/empty");
			return false;
		}

		// 1) 先查缓存：检查该会话是否已有客服
		String cacheKey = "conv:agents:" + conversationId;
		try {
			RedisCommands<String, String> commands = redisConnection.sync();
			String cachedAgents = commands.get(cacheKey);
			if (cachedAgents != null && !cachedAgents.isEmpty()) {
				log.debug("conversationId={} already has agents (from cache), cannot bindFirst agentId={}", conversationId, agentId);
				return false;
			}
		} catch (Exception e) {
			log.warn("failed to check cache for conversationId={}", conversationId, e);
		}

		// 2) 缓存miss，查数据库
		QueryWrapper<ChatConversationMemberDO> check = new QueryWrapper<>();
		check.eq("conversation_id", conversationId)
				.eq("member_type", "agent")
				.isNull("left_at");

		Long count = conversationMemberMapper.selectCount(check);
		if (count > 0) {
			// 回写缓存
			try {
				RedisCommands<String, String> commands = redisConnection.sync();
				commands.setex(cacheKey, 300, "hasAgents");
			} catch (Exception e) {
				log.warn("failed to update cache for conversationId={}", conversationId, e);
			}
			log.debug("conversationId={} already has agents (from DB), cannot bindFirst agentId={}", conversationId, agentId);
			return false;
		}

		// 2) 插入首个客服
		ChatConversationMemberDO member = new ChatConversationMemberDO();
		member.setConversationId(conversationId);
		member.setMemberType("agent");
		member.setMemberId(agentId);
		member.setJoinedAt(new Date());
		member.setLeftAt(null);

		int inserted = conversationMemberMapper.insert(member);
		boolean success = inserted > 0;
		
		if (success) {
			// 更新缓存：标记该会话已有客服
			try {
				RedisCommands<String, String> commands = redisConnection.sync();
				commands.setex(cacheKey, 300, "hasAgents");
			} catch (Exception e) {
				log.warn("failed to update cache after successful bind: conversationId={}", conversationId, e);
			}
		}
		
		log.info("bindFirst agentId={} to conversationId={}, success={}", agentId, conversationId, success);
		return success;
	}

}