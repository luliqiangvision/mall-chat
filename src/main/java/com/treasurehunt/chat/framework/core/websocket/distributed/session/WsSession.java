package com.treasurehunt.chat.framework.core.websocket.distributed.session;

import com.treasurehunt.chat.framework.core.websocket.mvc.model.WsResponseEntity;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

/**
 * 统一的 WS 会话接口，扩展元数据，并与 Spring 的 WebSocketSession 兼容
 */
public interface WsSession extends WebSocketSession {
    boolean isLocal();
    String getInstanceId();

    /**
     * 发送框架响应实体
     */
    void send(WsResponseEntity<?> response) throws IOException;
}


