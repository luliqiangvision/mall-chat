package com.treasurehunt.chat.framework.core.websocket.distributed.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WsResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;

import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 远程 WebSocket 会话实现（通过 Redis Stream 转发），实现 WsSession 以统一调用面
 */
public class RemoteWsSession implements WsSession {

    private final String sessionId;
    private final String instanceId;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RemoteWsSession(String sessionId, String instanceId, StatefulRedisConnection<String, String> redisConnection) {
        this.sessionId = sessionId;
        this.instanceId = instanceId;
        this.redisConnection = redisConnection;
    }

    // 统一发送封装（框架使用）
    public void send(WsResponseEntity<?> response) throws IOException {
        // 远程发送仅透传响应载荷与必要元数据
        String jsonMessage = objectMapper.writeValueAsString(response.getBody());
        Map<String, String> message = new HashMap<>();
        message.put("sessionId", sessionId);
        message.put("frameType", response.getFrameType().name());
        message.put("message", jsonMessage);
        message.put("timestamp", String.valueOf(System.currentTimeMillis()));
        String streamKey = "ws:inbox:" + instanceId;
        RedisCommands<String, String> commands = redisConnection.sync();
        commands.xadd(streamKey, message);
    }

    // WsSession 扩展
    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    // WebSocketSession 兼容（远程会话无法直接操作底层连接，提供合理占位实现）
    @Override
    public String getId() {
        return sessionId;
    }

    @Override
    public URI getUri() {
        return null;
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return HttpHeaders.EMPTY;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return java.util.Collections.emptyMap();
    }

    @Override
    public Principal getPrincipal() {
        return null;
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return null;
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return null;
    }

    @Override
    public String getAcceptedProtocol() {
        return null;
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getTextMessageSizeLimit() {
        return 0;
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return 0;
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return java.util.Collections.emptyList();
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        /* 走上面的 send */ }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public void close() throws IOException {
    }

    @Override
    public void close(CloseStatus status) throws IOException {
    }
}
