package com.treasurehunt.chat.component.routing;

import java.util.Set;

/**
 * 智能客服路由服务（群聊模式）
 * 
 * @author gaga
 * @since 2025-10-06
 */
public interface AgentRoutingService {

	/**
	 * 分配客服人员（客户->客服方向）
	 * @param conversationId 聊天窗口ID  
	 * @param isNewConversation 是否为新聊天窗口
	 * @return 该聊天窗口的客服session列表
	 */
	RouteResult assignAgents(String conversationId, boolean isNewConversation);
	/**
	 * 指派首个客服到聊天窗口（抢占式首绑）
	 * @param conversationId 聊天窗口ID
	 * @param agentId 首绑客服ID
	 * @return 是否成功抢占
	 */
	boolean bindFirstAgent(String conversationId, String agentId);
	/**
	 * 路由结果（群聊模式：多客服）
	 * - agentId → 第一个客服ID（兼容性字段）
	 * - targetSessionIds → 所有在线客服的 sessionId 列表
	 */
	class RouteResult {
		public final Set<String> agentIds;
		public final boolean hasOnlineAgent; // 是否有在线客服（人类）
		public RouteResult(Set<String> agentIds, boolean hasOnlineAgent) {
			this.agentIds = agentIds;
			this.hasOnlineAgent = hasOnlineAgent;
		}
	}
}



