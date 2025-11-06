package com.treasurehunt.chat.framework.core.websocket.distributed.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WsResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.web.socket.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * 本地 WebSocket 会话实现（委托底层 WebSocketSession）
 */
public class LocalWsSession implements WsSession {

    private final WebSocketSession delegate;
    private final String instanceId;

    public LocalWsSession(WebSocketSession delegate, String instanceId) {
        this.delegate = delegate;
        this.instanceId = instanceId;
    }

    public void send(WsResponseEntity<?> response) throws IOException {
        switch (response.getFrameType()) {
            case TEXT: {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(response.getBody());
                delegate.sendMessage(new TextMessage(json));
                break;
            }
            case BINARY: {
                byte[] bytes = toBytes(response.getBody());
                delegate.sendMessage(new BinaryMessage(bytes));
                break;
            }
            case PING: {
                delegate.sendMessage(new BinaryMessage(new byte[0]));
                break;
            }
            case PONG: {
                delegate.sendMessage(new BinaryMessage(new byte[0]));
                break;
            }
            case CLOSE: {
                delegate.close();
                break;
            }
            default: {
                ObjectMapper mapper = new ObjectMapper();
                String json = mapper.writeValueAsString(response.getBody());
                delegate.sendMessage(new TextMessage(json));
            }
        }
    }

    private byte[] toBytes(Object body) {
        if (body == null) return new byte[0];
        if (body instanceof byte[]) return (byte[]) body;
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.writeValueAsString(body).getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return body.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    @Override
    public boolean isLocal() {
        return true;
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    // WebSocketSession delegation
    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public URI getUri() {
        return delegate.getUri();
    }

    @Override
    public HttpHeaders getHandshakeHeaders() {
        return delegate.getHandshakeHeaders();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return delegate.getAttributes();
    }

    @Override
    public Principal getPrincipal() {
        return delegate.getPrincipal();
    }

    @Override
    public InetSocketAddress getLocalAddress() {
        return delegate.getLocalAddress();
    }

    @Override
    public InetSocketAddress getRemoteAddress() {
        return delegate.getRemoteAddress();
    }

    @Override
    public String getAcceptedProtocol() {
        return delegate.getAcceptedProtocol();
    }

    @Override
    public void setTextMessageSizeLimit(int messageSizeLimit) {
        delegate.setTextMessageSizeLimit(messageSizeLimit);
    }

    @Override
    public int getTextMessageSizeLimit() {
        return delegate.getTextMessageSizeLimit();
    }

    @Override
    public void setBinaryMessageSizeLimit(int messageSizeLimit) {
        delegate.setBinaryMessageSizeLimit(messageSizeLimit);
    }

    @Override
    public int getBinaryMessageSizeLimit() {
        return delegate.getBinaryMessageSizeLimit();
    }

    @Override
    public List<WebSocketExtension> getExtensions() {
        return delegate.getExtensions();
    }

    @Override
    public void sendMessage(WebSocketMessage<?> message) throws IOException {
        delegate.sendMessage(message);
    }

    @Override
    public boolean isOpen() {
        return delegate.isOpen();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public void close(CloseStatus status) throws IOException {
        delegate.close(status);
    }
}
