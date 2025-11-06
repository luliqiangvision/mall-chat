package com.treasurehunt.chat.wscontroller;

import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestMapping;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestBody;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsController;
import com.treasurehunt.chat.wsservice.AgentServiceChatService;
import com.treasurehunt.chat.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;

/**
 * 客服聊天 WebSocket 控制器
 * 专门处理客服发送过来的 WebSocket 请求
 */
@Slf4j
@Component
@WsController("/agent")
public class AgentServiceChatController {

    @Autowired
    private AgentServiceChatService agentServiceChatService;

    /**
     * 处理客服发送消息请求
     */
    @WsRequestMapping("/sendMessage")
    public WebSocketDataWrapper<ReplySendMessageResult> receiveMessage(@WsRequestBody ChatMessage chatMessage,
            WebSocketSession session)
            throws IOException {
        log.debug("处理客服发送消息请求: {}", chatMessage);
        // 从session中获取用户信息
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo == null) {
            log.error("会话中缺少用户信息");
            return null;
        }
        ReplySendMessageResult result = ReplySendMessageResult.builder().build();
        result.setClientMsgId(chatMessage.getClientMsgId());
        try {
            // 调用服务层处理发送消息逻辑
            result = agentServiceChatService.receiveMessage(chatMessage, session);
            // 发送结果响应,本身websocket没有响应这个概念的，不像http，一个发送对应一个响应（如果不是void的情况下），这里是为了展示给客户，自己的消息有没有发送成功而返回的响应
            return WebSocketDataWrapper.success("/replySendRequest", result, "200");
        } catch (Exception e) {
            log.error("处理客服发送消息失败", e);
            // 发送失败响应,把是哪条消息发送失败了返回给客户端
            return WebSocketDataWrapper.failure("/replySendRequest", "服务器处理客服发送信息失败", "500",result);
        }
    }

    /**
     * 处理客服拉取消息请求
     */
    @WsRequestMapping("/pullMessage")
    public WebSocketDataWrapper<PullMessageRequest> pullMessageHandler(
            @WsRequestBody PullMessageRequest pullMessageRequest, WebSocketSession session)
            throws IOException {
        log.debug("处理客服拉取消息请求: {}", pullMessageRequest);

        // 从session中获取用户信息
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo == null) {
            log.warn("会话中缺少用户信息");
            return WebSocketDataWrapper.failure("/pullMessageRequest", "会话中缺少用户信息", "500");
        }

        try {
            // 调用服务层处理拉取消息逻辑
            PullMessageRequest request = agentServiceChatService.pullMessage(pullMessageRequest, session);
            return WebSocketDataWrapper.success("/pullMessageRequest", request, "200");
        } catch (Exception e) {
            log.error("处理客服拉取消息失败", e);
            return WebSocketDataWrapper.failure("/pullMessageRequest", "处理客服拉取消息失败", "500");
        }
    }

    /**
     * 客服端心跳
     */
    @WsRequestMapping("/heartbeat")
    public WebSocketDataWrapper<HeartbeatResponse> heartbeat(@WsRequestBody HeartbeatRequest heartbeatRequest,
            WebSocketSession session) throws IOException {
        log.debug("处理客服心跳: {}", heartbeatRequest);
        try {
            HeartbeatResponse response = agentServiceChatService.heartbeat(heartbeatRequest, session);
            return WebSocketDataWrapper.success("/heartbeat", response, "200");
        } catch (Exception e) {
            log.error("处理客服心跳失败", e);
            return WebSocketDataWrapper.failure("/heartbeat", "处理客服心跳失败", "500");
        }
    }

    /**
     * 
     * 重连检查是否有跳号的接口
     * 就是如果重连了,要先查询有没有新消息,比如客户原本连接的实例A宕机了,重新连到实例B,但是实例B的广播可能已经消费过客户掉线期间的消息了,
     * 这时候需要先查询数据库有没有错过的消息,这时候就不保证客户端的消息显示顺序了,因为实例B随时可能推送给客户端新消息,可以协同客户端一起
     * 来控制,如果遇到跳号或者空洞的消息,要先查询数据库有没有丢,如果确实数据库也没有,才可以显示出来
     */
    @WsRequestMapping("/checkMessage")
    public WebSocketDataWrapper<List<ChatMessage>> checkReconnectMessage(@WsRequestBody CheckMessageRequest request,
            WebSocketSession session)
            throws IOException {
        log.debug("处理客服重连补齐请求: {}", request);
        // 从session中获取用户信息
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo == null) {
            log.warn("会话中缺少用户信息");
            return WebSocketDataWrapper.failure("/checkMessageRequest", "会话中缺少用户信息", "500");
        }
        try {
            List<ChatMessage> messages = agentServiceChatService.checkReconnectMessages(request, session);
            return WebSocketDataWrapper.success("/checkMessageRequest", messages, "200");
        } catch (Exception e) {
            log.error("处理客服重连补齐失败", e);
            return WebSocketDataWrapper.failure("/checkMessageRequest", "处理客服重连补齐失败", "500");
        }
    }

}
