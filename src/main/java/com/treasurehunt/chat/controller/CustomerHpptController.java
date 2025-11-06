package com.treasurehunt.chat.controller;

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
 * 处理客户登录时需要同步查询的接口
 */
@Slf4j
@RestController
@RequestMapping("/chat/customer-service/conversation")
public class CustomerHpptController {

    @Autowired
    private CustomerHttpService customerHttpService;

    /**
     * 客户检查未读消息
     * 
     * @param request 检查未读消息请求
     * @return 未读消息检查结果
     */
    @PostMapping("/checkUnreadMessages")
    public CommonResult<CheckUnreadMessagesResponse> checkUnreadMessages(@RequestBody CheckUnreadMessagesRequest request) {
        log.debug("处理客户检查未读消息请求: userId={}, request={}", request.getUserId(), request);
        
        try {
            CheckUnreadMessagesResponse response = customerHttpService.checkUnreadMessages(request.getUserId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客户检查未读消息失败: userId={}", request.getUserId(), e);
            throw new RuntimeException("检查未读消息失败", e);
        }
    }

    /**
     * 客户获取会话列表
     * 
     * @param request 请求参数
     * @return 会话列表
     */
    @PostMapping("/listConversations")
    public CommonResult<CheckUnreadMessagesResponse> getConversations(@RequestBody CustomerConversationsRequest request) {
        log.debug("处理客户获取会话列表请求: userId={}", request.getUserId());
        
        try {
            CheckUnreadMessagesResponse response = customerHttpService.checkUnreadMessages(request.getUserId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客户获取会话列表失败: userId={}", request.getUserId(), e);
            throw new RuntimeException("获取会话列表失败", e);
        }
    }

    /**
     * 获取聊天窗口列表
     * 
     * @param request 获取聊天窗口列表请求
     * @return 会话视图Map
     */
    @PostMapping("/getChatWindowList")
    public CommonResult<Map<String, ConversationViewVO>> getChatWindowList(@RequestBody InitConversationViewRequest request) {
        log.debug("处理客户获取聊天窗口列表请求: userId={}", request.getUserId());
        
        try {
            Map<String, ConversationViewVO> response = customerHttpService.getChatWindowList(Long.valueOf(request.getUserId()));
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客户获取聊天窗口列表失败: userId={}", request.getUserId(), e);
            throw new RuntimeException("获取聊天窗口列表失败", e);
        }
    }

    @Autowired
    private ConversationService conversationService;

    /**
     * 会话预检接口
     * 检查用户在指定店铺是否有历史会话
     */
    @PostMapping("/check")
    public CommonResult<ConversationCheckResponse> checkConversation(@RequestBody ConversationCheckRequest request) {
        log.debug("收到会话预检请求: {}", request);
        return CommonResult.success(conversationService.checkConversation(request));
    }

    /**
     * 客户分页拉取历史消息
     * 
     * @param request 分页拉取请求
     * @return 分页消息结果
     */
    @PostMapping("/pullMessageWithPagedQuery")
    public CommonResult<ChatmessageWithPaged> pullMessageWithPagedQuery(@RequestBody CustomerPullMessageWithPagedQueryRequest request, @RequestHeader("X-User-Id") String userId) {
        log.debug("处理客户分页拉取消息请求: userId={}, request={}", userId, request);
        
        try {
            ChatmessageWithPaged response = customerHttpService.pullMessageWithPagedQuery(request, Long.valueOf(userId));
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客户分页拉取消息失败: userId={}", userId, e);
            throw new RuntimeException("分页拉取消息失败", e);
        }
    }

    /**
     * 根据conversationId初始化聊天窗口
     * 入参：conversationId，用户ID从请求头 X-User-Id 读取
     * 规则：全部未读 + 未读最小ID之前5条；若无未读则最近10条
     */
    @PostMapping("/initChatWindowByConversationId")
    public CommonResult<ChatMessagesInitResult> initChatWindowByConversationId(@RequestBody String conversationId, @RequestHeader("X-User-Id") String userId) {
        log.debug("根据conversationId初始化聊天窗口: userId={}, conversationId={}", userId, conversationId);
        try {
            ChatMessagesInitResult resp = customerHttpService.initChatWindowByConversationId(conversationId, Long.valueOf(userId));
            return CommonResult.success(resp);
        } catch (Exception e) {
            log.error("根据conversationId初始化聊天窗口失败: userId={}, conversationId={}", userId, conversationId, e);
            throw new RuntimeException("根据conversationId初始化聊天窗口失败", e);
        }
    }

    /**
     * 检查缺失的消息
     * 
     * @param request 检查缺失消息请求
     * @param userId 用户ID
     * @return 缺失消息响应
     */
    @PostMapping("/checkMissingMessages")
    public CommonResult<CheckMissingMessagesResponse> checkMissingMessages(@RequestBody CheckMissingMessagesRequest request, @RequestHeader("X-User-Id") String userId) {
        log.debug("处理客户检查缺失消息请求: userId={}, request={}", userId, request);
        
        try {
            CheckMissingMessagesResponse response = customerHttpService.checkMissingMessages(request, Long.valueOf(userId));
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客户检查缺失消息失败: userId={}", userId, e);
            throw new RuntimeException("检查缺失消息失败", e);
        }
    }

    /**
     * 标记已读
     * 
     * @param request 标记已读请求
     * @param userId 用户ID
     * @return 是否成功
     */
    @PostMapping("/markAsRead")
    public CommonResult<Boolean> markAsRead(@RequestBody MarkAsReadRequest request, @RequestHeader("X-User-Id") String userId) {
        log.debug("处理客户标记已读请求: userId={}, request={}", userId, request);
        
        try {
            boolean result = customerHttpService.markAsRead(request, Long.valueOf(userId));
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("处理客户标记已读失败: userId={}", userId, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}
