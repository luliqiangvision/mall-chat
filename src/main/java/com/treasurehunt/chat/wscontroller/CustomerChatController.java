package com.treasurehunt.chat.wscontroller;

import com.treasurehunt.chat.vo.ChatMessage;

import com.treasurehunt.chat.vo.CheckMessageRequest;
import com.treasurehunt.chat.vo.PullMessageRequest;
import com.treasurehunt.chat.wsservice.CustomerChatService;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestBody;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsRequestMapping;
import com.treasurehunt.chat.framework.core.websocket.mvc.model.WebSocketDataWrapper;
import com.treasurehunt.chat.vo.ReplySendMessageResult;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import com.treasurehunt.chat.vo.HeartbeatRequest;
import com.treasurehunt.chat.vo.HeartbeatResponse;

import lombok.extern.slf4j.Slf4j;
import com.treasurehunt.chat.framework.core.websocket.mvc.annotation.WsController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;


/**
 * 客户聊天 WebSocket 控制器
 * 专门处理客户发送过来的websocket请求
 */
@Slf4j
@Component
@WsController
public class CustomerChatController {
    
    @Autowired
    private CustomerChatService customerChatService;
    
    /**
     * 处理客户发送消息请求
     */
    @WsRequestMapping("/sendMessage")
    public WebSocketDataWrapper<ReplySendMessageResult> receiveMessage(@WsRequestBody ChatMessage chatMessage, WebSocketSession session) throws IOException {
        log.debug("处理客服发送消息请求: {}", chatMessage);
        ReplySendMessageResult result = ReplySendMessageResult.builder().build();
        result.setClientMsgId(chatMessage.getClientMsgId());
        // 从session中获取用户信息
        WebSocketUserInfo userInfo = (WebSocketUserInfo) session.getAttributes().get("userInfo");
        if (userInfo == null) {
            log.error("会话中缺少用户信息");
            return WebSocketDataWrapper.failure("/replySendRequest", "会话中缺少用户信息", "500");
        }
        try {
            // 调用服务层处理发送消息逻辑
            ReplySendMessageResult replySendMessageResult = customerChatService.receiveMessage(chatMessage, session);
            // 直接发送WebSocket响应给客户端
            return WebSocketDataWrapper.success("/replySendRequest", replySendMessageResult, "200");
        } catch (Exception e) {
            log.error("处理客户发送消息失败", e);
            // 发送失败响应,把是哪条消息发送失败了返回给客户端
            return WebSocketDataWrapper.failure("/replySendRequest", "处理客户发送消息失败", "500",result);
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
	public WebSocketDataWrapper<List<ChatMessage>> checkReconnectMessage(@WsRequestBody CheckMessageRequest checkMessageRequest, WebSocketSession session) throws IOException {
        log.debug("处理客户重连检查是否有跳号请求: {}", checkMessageRequest);
        try {
            List<ChatMessage> messages = customerChatService.checkReconnectMessages(checkMessageRequest, session);
            return WebSocketDataWrapper.success("/checkMessageRequest", messages, "200");
        } catch (Exception e) {
            log.error("处理客户重连检查失败", e);
            return WebSocketDataWrapper.failure("/checkMessageRequest", "处理客户重连检查失败", "500");
        }
    }
    
    /**
     * 处理客户拉取消息请求
     */
    @WsRequestMapping("/pullMessage")
    public WebSocketDataWrapper<PullMessageRequest> pullMessageHandler(@WsRequestBody PullMessageRequest pullMessageRequest, WebSocketSession session) throws IOException {
        log.debug("处理客户拉取消息请求: {}", pullMessageRequest);
        try {
            // 调用服务层处理拉取消息逻辑
            PullMessageRequest chatMessages = customerChatService.pullMessage(pullMessageRequest, session);
            return WebSocketDataWrapper.success("/pullMessageRequest", chatMessages, "200");
        } catch (Exception e) {
            log.error("处理客户拉取消息失败", e);
            return WebSocketDataWrapper.failure("/pullMessageRequest", "处理客户拉取消息失败", "500");
        }
    }
    
	/**
	 * 客户端心跳
	 */
	@WsRequestMapping("/heartbeat")
	public WebSocketDataWrapper<HeartbeatResponse> heartbeat(@WsRequestBody HeartbeatRequest heartbeatRequest, WebSocketSession session) throws IOException {
		log.debug("处理客户心跳: {}", heartbeatRequest);
		try {
			HeartbeatResponse response = customerChatService.heartbeat(heartbeatRequest, session);
			return WebSocketDataWrapper.success("/heartbeat", response, "200");
		} catch (Exception e) {
			log.error("处理客户心跳失败", e);
			return WebSocketDataWrapper.failure("/heartbeat", "处理客户心跳失败", "500");
		}
	}
}
