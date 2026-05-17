package com.treasurehunt.chat.component.routing.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.ChatConversationMemberMapper;
import com.treasurehunt.chat.component.manager.ChatWindowManager;
import com.treasurehunt.chat.service.AgentManagementService;
import com.treasurehunt.chat.service.ConversationService;
import com.treasurehunt.chat.component.routing.AgentRoutingService;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

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

	@Autowired
	private ChatConversationMapper conversationMapper;

	@Autowired
	private ConversationService conversationService;

	/**
	 * 为聊天窗口分配客服（群聊模式）,这个方法目前只做数据库层面上的绑定,缓存没有实现,以后看情况是否要实现,抄送的功能不在这里,这里只管分配
	 * 
	 * 场景1：新聊天窗口创建
	 * 聊天窗口不存在 → 有店走店铺售前池（优先在线，否则离线售前，不走 corporate）；无店走公司级（{@code corporate}）
	 * 同时抄送给机器人
	 * 
	 * 场景2：已存在聊天窗口，客服还在群里
	 * 聊天窗口存在，客服成员还在 → 推送给群成员
	 * 同时抄送给机器人
	 * 
	 * 场景3：已存在聊天窗口，所有客服都退群了
	 * 聊天窗口存在，但 chat_conversation_member 中没有人类客服了 → 同场景1规则重新绑定
	 * 同时抄送给机器人
	 */
	@Override
	public RouteResult assignAgents(String conversationId, boolean isNewConversation, String businessLine,
			Long shopId) {
		if (conversationId == null || conversationId.isEmpty()) {
			log.warn("conversationId is null/empty");
			return new RouteResult(null, false);
		}
		if (businessLine == null || businessLine.isEmpty()) {
			log.warn("业务线为空，无法分配客服: conversationId={}", conversationId);
			return new RouteResult(null, false);
		}
		String resolvedBusinessLine = businessLine.trim();

		// 场景1：新会话创建
		if (isNewConversation) {
			return bindInboundAgent(conversationId, resolvedBusinessLine, shopId);
		}

		// 场景2&3：已存在会话
		return assignExistingOrPreSalesAgents(conversationId, resolvedBusinessLine, shopId);
	}

	/**
	 * 新会话或场景3：有店仅店铺售前池；无店公司级池。绑定群成员并同步主接待 {@code agent_id}。
	 */
	private RouteResult bindInboundAgent(String conversationId, String businessLine, Long shopId) {
		log.info("客户进线分配: conversationId={}, businessLine={}, shopId={}", conversationId, businessLine, shopId);

		String agentId = agentManagementService.resolveLeastLoadedAgentForCustomerRouting(businessLine, shopId);

		if (agentId == null || agentId.isEmpty()) {
			if (shopId != null) {
				log.error(
						"[SHOP_AGENT_ROUTING] 有店进线未分配到主接待（常见原因见 [SHOP_PRE_SALES_POOL_EMPTY]）: conversationId={}, businessLine={}, shopId={}, [TODO: alerting]",
						conversationId, businessLine, shopId);
			} else {
				log.warn("无可用客服(无 shopId，公司级池为空): conversationId={}, businessLine={}", conversationId,
						businessLine);
			}
			return new RouteResult(null, false);
		}

		chatWindowManager.bindAgents(conversationId, Collections.singletonList(agentId));
		syncConversationPrimaryAgent(conversationId, agentId);

		log.info("已绑定客服: conversationId={}, agentId={}", conversationId, agentId);

		return new RouteResult(agentId, true);
	}

	private void syncConversationPrimaryAgent(String conversationId, String agentId) {
		UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
		updateWrapper.eq("conversation_id", conversationId)
				.set("agent_id", agentId)
				.set("updated_at", new Date());
		conversationMapper.update(null, updateWrapper);
	}

	/**
	 * 场景2&3：已存在会话 - 检查现有客服或分配新客服
	 */
	private RouteResult assignExistingOrPreSalesAgents(String conversationId, String businessLine, Long shopId) {
		// 1. 查询现有客服成员
		QueryWrapper<ChatConversationMemberDO> memberQuery = new QueryWrapper<>();
		memberQuery.eq("conversation_id", conversationId)
				   .eq("business_line", businessLine)
				   .eq("member_type", "agent")
				   .isNull("left_at");

		List<ChatConversationMemberDO> members = conversationMemberMapper.selectList(memberQuery);
		
		if (!members.isEmpty()) {
			// 场景2：客服还在群里，取第一个客服ID（只返回一个）
			String firstAgentId = members.get(0).getMemberId();
			
			log.info("场景2：客服还在群里，推送给群成员: conversationId={}, agentId={}", conversationId, firstAgentId);
			
			return new RouteResult(firstAgentId, true);
		}
		
		// 场景3：所有客服都退群了，按店铺/公司级重新分配
		log.info("场景3：所有客服都退群了，重新分配客服: conversationId={}", conversationId);
		return bindInboundAgent(conversationId, businessLine, shopId);
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
		String businessLine = conversationService.requireBusinessLineByConversationId(conversationId);

		QueryWrapper<ChatConversationMemberDO> check = new QueryWrapper<>();
		check.eq("conversation_id", conversationId)
				.eq("business_line", businessLine)
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
		conversationService.enrichMemberBusinessLine(member);

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