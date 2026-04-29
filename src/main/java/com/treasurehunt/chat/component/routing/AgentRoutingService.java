package com.treasurehunt.chat.component.routing;

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
	 * @param businessLine 业务线
	 * @return 该聊天窗口的客服ID
	 */
	RouteResult assignAgents(String conversationId, boolean isNewConversation, String businessLine);
	/**
	 * 指派首个客服到聊天窗口（抢占式首绑）
	 * @param conversationId 聊天窗口ID
	 * @param agentId 首绑客服ID
	 * @return 是否成功抢占
	 */
	boolean bindFirstAgent(String conversationId, String agentId);
	/**
	 * 路由结果（只分配一个售前客服）
	 * - agentId → 分配的客服ID（可为null，表示没有客服）
	 * - hasOnlineAgent → 是否有在线客服（人类）
	 */
	class RouteResult {
		public final String agentId;
		public final boolean hasOnlineAgent; // 是否有在线客服（人类）
		public RouteResult(String agentId, boolean hasOnlineAgent) {
			this.agentId = agentId;
			this.hasOnlineAgent = hasOnlineAgent;
		}
	}
}



