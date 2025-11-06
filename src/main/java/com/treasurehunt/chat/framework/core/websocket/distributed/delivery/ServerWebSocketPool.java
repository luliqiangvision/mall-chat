package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 服务器间 WebSocket 连接池
 * 
 * 职责：
 * - 管理到其他实例的 WebSocket 连接
 * - 提供连接复用和自动重连
 * - 支持心跳检测和连接健康检查
 * 
 * @author gaga
 * @since 2025-10-06
 */
@Slf4j
@Component
public class ServerWebSocketPool {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	@Qualifier("customNacosServiceDiscovery")
	private NacosServiceDiscovery nacosServiceDiscovery;

	// 服务器间连接池
	private Map<String, WebSocketSession> serverConnections = new ConcurrentHashMap<>();

	/**
	 * 发送消息到目标实例（泛型方法）
	 * @param targetInstanceAddress 目标实例地址 (IP:Port)
	 * @param message 消息内容（任意类型）
	 * @param <T> 消息类型
	 * @return 是否发送成功
	 */
	public <T> boolean sendMessage(String targetInstanceAddress, T message) {
		try {
			WebSocketSession session = getConnection(targetInstanceAddress);
			if (session != null && session.isOpen()) {
				String messageJson = objectMapper.writeValueAsString(message);
				session.sendMessage(new TextMessage(messageJson));
				log.debug("Sent message to instance: {}, message: {}", targetInstanceAddress, messageJson);
				return true;
			} else {
				log.warn("Connection to instance {} is not available", targetInstanceAddress);
				return false;
			}
		} catch (Exception e) {
			log.error("Failed to send message to instance: {}", targetInstanceAddress, e);
			// 连接失败，移除连接
			serverConnections.remove(targetInstanceAddress);
			return false;
		}
	}

    /**
     * 本机直发：使用已有的本地 WebSocketSession 发送消息
     */
    public <T> boolean sendMessage(WebSocketSession session, T message) {
        if (session == null || !session.isOpen()) return false;
        try {
            String messageJson = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(messageJson));
            log.debug("Sent message via local session: {}", session.getId());
            return true;
        } catch (Exception e) {
            log.error("Failed to send message via local session: {}", session != null ? session.getId() : null, e);
            return false;
        }
    }

	/**
	 * 获取到目标实例的连接
	 * @param targetInstanceAddress 目标实例地址 (IP:Port)
	 * @return WebSocket连接
	 */
	private WebSocketSession getConnection(String targetInstanceAddress) {
		WebSocketSession session = serverConnections.get(targetInstanceAddress);
		
		if (session == null || !session.isOpen()) {
			// 重新建立连接
			session = createConnection(targetInstanceAddress);
			if (session != null) {
				serverConnections.put(targetInstanceAddress, session);
			}
		}
		
		return session;
	}

	/**
	 * 创建到目标实例的连接
	 * @param targetInstanceAddress 目标实例地址 (IP:Port)
	 * @return WebSocket连接
	 */
	private WebSocketSession createConnection(String targetInstanceAddress) {
		try {
			String[] parts = targetInstanceAddress.split(":");
			if (parts.length != 2) {
				log.warn("Invalid instance address format: {}", targetInstanceAddress);
				return null;
			}

			String ip = parts[0];
			int port = Integer.parseInt(parts[1]);

			WebSocketClient client = new StandardWebSocketClient();
			WebSocketHandler handler = new TextWebSocketHandler() {
				@Override
				public void afterConnectionEstablished(WebSocketSession session) {
					log.info("Server-to-server WebSocket connection established to: {}", targetInstanceAddress);
				}

				@Override
				public void handleTransportError(WebSocketSession session, Throwable exception) {
					log.error("Server-to-server WebSocket transport error for {}: {}", targetInstanceAddress, exception.getMessage());
					serverConnections.remove(targetInstanceAddress); // 连接出错，移除连接
				}

				@Override
				public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
					log.info("Server-to-server WebSocket connection closed for {}: {}", targetInstanceAddress, status);
					serverConnections.remove(targetInstanceAddress); // 连接关闭，移除连接
				}
			};

			String wsUrl = "ws://" + ip + ":" + port + "/server-websocket";
			log.info("Attempting to connect to server-to-server WebSocket: {}", wsUrl);
			return client.doHandshake(handler, wsUrl).get(5, TimeUnit.SECONDS); // 5秒超时
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			log.error("Failed to establish server-to-server WebSocket connection to {}: {}", targetInstanceAddress, e.getMessage());
			return null;
		}
	}

	/**
	 * 心跳检测
	 */
	public void heartbeatCheck() {
		serverConnections.forEach((instanceId, session) -> {
			try {
				if (session.isOpen()) {
					session.sendMessage(new TextMessage("ping"));
				} else {
					log.warn("Connection to instance {} is closed, removing", instanceId);
					serverConnections.remove(instanceId);
				}
			} catch (Exception e) {
				log.warn("Heartbeat failed for instance: {}, removing", instanceId);
				serverConnections.remove(instanceId);
			}
		});
	}

	/**
	 * 关闭所有连接
	 */
	public void closeAllConnections() {
		serverConnections.forEach((instanceId, session) -> {
			try {
				if (session.isOpen()) {
					session.close();
				}
			} catch (Exception e) {
				log.warn("Failed to close connection to instance: {}", instanceId, e);
			}
		});
		serverConnections.clear();
	}
}
