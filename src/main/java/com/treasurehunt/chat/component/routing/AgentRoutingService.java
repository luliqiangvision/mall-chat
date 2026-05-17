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
	 * <p>有 {@code shopId}：仅从店铺售前绑定池选主接待（优先在线，均离线则选离线售前，不回退 {@code corporate}）。
	 * 无 {@code shopId}：同业务线 {@code corporate}。无 {@code businessLine} 时调用方应跳过分配。
	 * 不以 {@code tenant_id} 为选人依据（与 {@code chat_conversation.tenant_id} 元数据无关）。
	 *
	 * @param conversationId   聊天窗口 ID
	 * @param isNewConversation  是否为新聊天窗口
	 * @param businessLine     业务线（须与网关 / 会话一致）
	 * @param shopId             店铺业务 ID，与 {@code mall_shop.shop_id} 一致；无店为 null
	 * @return 该聊天窗口的客服 ID
	 */
	RouteResult assignAgents(String conversationId, boolean isNewConversation, String businessLine, Long shopId);
	/**
	 * 指派首个客服到聊天窗口（抢占式首绑）
	 * @param conversationId 聊天窗口ID
	 * @param agentId 首绑客服ID
	 * @return 是否成功抢占
	 */
	boolean bindFirstAgent(String conversationId, String agentId);
	/**
	 * 路由结果（只分配一名人类客服）
	 * - agentId → 分配的客服ID（可为 null，表示未分到人）
	 * - hasAssignedHumanAgent → 是否成功分配到人类客服（{@code agentId != null}）
	 */
	class RouteResult {
		public final String agentId;
		/** 是否成功分配到人类客服；与机器人是否发忙碌/非工时提示无关。 */
		public final boolean hasAssignedHumanAgent;
		public RouteResult(String agentId, boolean hasAssignedHumanAgent) {
			this.agentId = agentId;
			this.hasAssignedHumanAgent = hasAssignedHumanAgent;
		}
	}
}



