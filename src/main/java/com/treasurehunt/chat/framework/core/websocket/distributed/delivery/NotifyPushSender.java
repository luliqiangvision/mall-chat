package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

/**
 * NotifyPushSender：将标准的 notifyPull 响应包，推送给本地 WebSocket 会话。
 * 仅处理本机直推场景；跨实例由对端实例的收件箱消费者负责转发。
 */
@Component
@Slf4j
public class NotifyPushSender {

	@Autowired
	private UserSessionMetadataManager userSessionMetadataManager;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public void sendNotifyPullLocal(String sessionId, String conversationId, long serverMsgId) {
		WebSocketSession session = userSessionMetadataManager.getLocalSession(sessionId);
		if (session == null || !session.isOpen()) return;
		try {
			Map<String, Object> env = new HashMap<>();
			env.put("interfaceName", "/notifyPull");
			env.put("version", 1);
			env.put("success", true);
			env.put("errorMessage", null);
			Map<String, Object> payload = new HashMap<>();
			payload.put("conversationId", conversationId);
			payload.put("timestamp", System.currentTimeMillis());
			payload.put("serverMsgId", serverMsgId);
			payload.put("status", "PENDING");
			env.put("payload", payload);
			String json = objectMapper.writeValueAsString(env);
			session.sendMessage(new TextMessage(json));
		} catch (Exception ignored) { }
	}
}


