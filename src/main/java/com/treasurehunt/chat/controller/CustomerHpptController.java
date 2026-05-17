package com.treasurehunt.chat.controller;

import com.treasurehunt.chat.context.ChatRequestContext;
import com.treasurehunt.chat.service.ConversationService;
import com.treasurehunt.chat.httpservice.CustomerHttpService;
import com.treasurehuntshop.mall.common.api.commonUtil.CommonResult;
import com.treasurehunt.chat.vo.CheckUnreadMessagesRequest;
import com.treasurehunt.chat.vo.CheckUnreadMessagesResponse;
import com.treasurehunt.chat.vo.ConversationCheckRequest;
import com.treasurehunt.chat.vo.ConversationCheckResponse;
import com.treasurehunt.chat.vo.InitConversationViewRequest;
import com.treasurehunt.chat.vo.CustomerConversationsRequest;
import com.treasurehunt.chat.vo.CheckMissingMessagesRequest;
import com.treasurehunt.chat.vo.CheckMissingMessagesResponse;
import com.treasurehunt.chat.vo.CustomerPullMessageWithPagedQueryRequest;
import com.treasurehunt.chat.vo.ConversationViewVO;
import com.treasurehunt.chat.vo.ChatMessagesInitResult;
import com.treasurehunt.chat.vo.ChatmessageWithPaged;
import com.treasurehunt.chat.vo.MarkAsReadRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 客户聊天HTTP接口控制器
 * 业务线由 {@link com.treasurehunt.chat.config.ChatRequestContextInterceptor} 从请求头
 * {@code X-Business-Line} 解析（与 mall-portal、客服 HTTP 一致）。
 */
@Slf4j
@RestController
@RequestMapping("/chat/customer-service/conversation")
public class CustomerHpptController {

    @Autowired
    private CustomerHttpService customerHttpService;

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/checkUnreadMessages")
    public CommonResult<CheckUnreadMessagesResponse> checkUnreadMessages(@RequestBody CheckUnreadMessagesRequest request,
                                                                         @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户检查未读消息请求: userId={}, businessLine={}, request={}", userId, businessLine, request);

        try {
            CheckUnreadMessagesResponse response = customerHttpService.checkUnreadMessages(Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客户检查未读消息失败: userId={}", userId, e);
            throw new RuntimeException("检查未读消息失败", e);
        }
    }

    @PostMapping("/listConversations")
    public CommonResult<CheckUnreadMessagesResponse> getConversations(@RequestBody CustomerConversationsRequest request,
                                                                      @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户获取会话列表请求: userId={}, businessLine={}", userId, businessLine);

        try {
            CheckUnreadMessagesResponse response = customerHttpService.checkUnreadMessages(Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客户获取会话列表失败: userId={}", userId, e);
            throw new RuntimeException("获取会话列表失败", e);
        }
    }

    @PostMapping("/getChatWindowList")
    public CommonResult<Map<String, ConversationViewVO>> getChatWindowList(@RequestBody InitConversationViewRequest request,
                                                                            @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户获取聊天窗口列表请求: userId={}, businessLine={}", userId, businessLine);

        try {
            Map<String, ConversationViewVO> response = customerHttpService.getChatWindowList(Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客户获取聊天窗口列表失败: userId={}", userId, e);
            throw new RuntimeException("获取聊天窗口列表失败", e);
        }
    }

    @PostMapping("/check")
    public CommonResult<ConversationCheckResponse> checkConversation(@RequestBody ConversationCheckRequest request,
                                                                     @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        request.setUserId(userId);
        log.debug("收到会话预检请求: businessLine={}, request={}", businessLine, request);
        return CommonResult.buildSuccess(conversationService.checkConversation(request, businessLine));
    }

    @PostMapping("/pullMessageWithPagedQuery")
    public CommonResult<ChatmessageWithPaged> pullMessageWithPagedQuery(@RequestBody CustomerPullMessageWithPagedQueryRequest request,
                                                                         @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户分页拉取消息请求: userId={}, businessLine={}, request={}", userId, businessLine, request);

        try {
            ChatmessageWithPaged response = customerHttpService.pullMessageWithPagedQuery(request, Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客户分页拉取消息失败: userId={}", userId, e);
            throw new RuntimeException("分页拉取消息失败", e);
        }
    }

    @PostMapping("/initChatWindowByConversationId")
    public CommonResult<ChatMessagesInitResult> initChatWindowByConversationId(@RequestBody String conversationId,
                                                                                @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("根据conversationId初始化聊天窗口: userId={}, businessLine={}, conversationId={}", userId, businessLine, conversationId);
        try {
            ChatMessagesInitResult resp = customerHttpService.initChatWindowByConversationId(conversationId, Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(resp);
        } catch (Exception e) {
            log.error("根据conversationId初始化聊天窗口失败: userId={}, conversationId={}", userId, conversationId, e);
            throw new RuntimeException("根据conversationId初始化聊天窗口失败", e);
        }
    }

    @PostMapping("/checkMissingMessages")
    public CommonResult<CheckMissingMessagesResponse> checkMissingMessages(@RequestBody CheckMissingMessagesRequest request,
                                                                           @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户检查缺失消息请求: userId={}, businessLine={}, request={}", userId, businessLine, request);

        try {
            CheckMissingMessagesResponse response = customerHttpService.checkMissingMessages(request, Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客户检查缺失消息失败: userId={}", userId, e);
            throw new RuntimeException("检查缺失消息失败", e);
        }
    }

    @PostMapping("/markAsRead")
    public CommonResult<Boolean> markAsRead(@RequestBody MarkAsReadRequest request,
                                            @RequestHeader("X-User-Id") String userId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客户标记已读请求: userId={}, businessLine={}, request={}", userId, businessLine, request);

        try {
            boolean result = customerHttpService.markAsRead(request, Long.valueOf(userId), businessLine);
            return CommonResult.buildSuccess(result);
        } catch (Exception e) {
            log.error("处理客户标记已读失败: userId={}", userId, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}
