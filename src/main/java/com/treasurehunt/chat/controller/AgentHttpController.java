package com.treasurehunt.chat.controller;

import com.treasurehunt.chat.httpservice.AgentHttpService;
import com.treasurehunt.chat.utils.BusinessLineResolver;
import com.treasurehunt.chat.vo.*;
import com.treasurehuntshop.mall.common.api.commonUtil.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 客服聊天HTTP接口控制器
 * 处理客服登录时需要同步查询的接口
 */
@Slf4j
@RestController
@RequestMapping("/chat/agent-service/conversation")
public class AgentHttpController {

    @Autowired
    private AgentHttpService agentHttpService;

    /**
     * 客服获取活跃会话列表
     * 
     * @param request 请求参数
     * @return 活跃会话列表
     */
    @PostMapping("/listConversations")
    public CommonResult<ActiveConversations> getConversations(@RequestBody AgentConversationsRequest request,
                                                              @RequestHeader("X-User-Id") String agentId,
                                                              @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服获取会话列表请求: agentId={}, businessLine={}", agentId, businessLine);
        
        try {
            ActiveConversations response = agentHttpService.getConversations(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取会话列表失败", e);
        }
    }

    /**
     * 客服获取待分配会话列表
     * 
     * @param request 请求参数
     * @return 待分配会话列表
     */
    @PostMapping("/unassignedConversations")
    public CommonResult<GetUnassignedConversationsResult> getUnassignedConversations(@RequestBody AgentConversationsRequest request,
                                                                                      @RequestHeader("X-User-Id") String agentId,
                                                                                      @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服获取待分配会话列表请求: agentId={}, businessLine={}", agentId, businessLine);
        
        try {
            GetUnassignedConversationsResult response = agentHttpService.getUnassignedConversations(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取待分配会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取待分配会话列表失败", e);
        }
    }

    /**
     * 客服加入会话
     * 
     * @param request 加入会话请求
     * @return 加入结果
     */
    @PostMapping("/joinConversation")
    public CommonResult<JoinConversationResult> joinConversation(@RequestBody JoinConversationRequest request,
                                                                 @RequestHeader("X-User-Id") String agentId,
                                                                 @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服加入会话请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
        try {
            JoinConversationResult response = agentHttpService.grapConversation(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服加入会话失败: agentId={}", agentId, e);
            throw new RuntimeException("加入会话失败", e);
        }
    }

    /**
     * 客服分页拉取历史消息
     * 
     * @param request 分页拉取请求
     * @return 分页消息结果
     */
    @PostMapping("/pullMessageWithPagedQuery")
    public CommonResult<ChatmessageWithPaged> pullMessageWithPagedQuery(@RequestBody PullMessageWithPagedQueryRequest request,
                                                                         @RequestHeader("X-User-Id") String agentId,
                                                                         @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服分页拉取消息请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
        try {
            ChatmessageWithPaged response = agentHttpService.pullMessageWithPagedQuery(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服分页拉取消息失败: agentId={}", agentId, e);
            throw new RuntimeException("分页拉取消息失败", e);
        }
    }

    /**
     * 客服删除会话
     * 
     * @param request 删除会话请求
     * @return 删除结果
     */
    @PostMapping("/deleteConversation")
    public CommonResult<String> deleteConversation(@RequestBody AgentDeleteConversationRequest request,
                                                   @RequestHeader("X-User-Id") String agentId,
                                                   @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服删除会话请求: agentId={}, businessLine={}, conversationId={}", agentId, businessLine, request.getConversationId());
        
        try {
            String response = agentHttpService.deleteConversation(request.getConversationId(), agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服删除会话失败: agentId={}, conversationId={}", agentId, request.getConversationId(), e);
            throw new RuntimeException("删除会话失败", e);
        }
    }

    /**
     * 获取聊天窗口列表
     * 
     * @param request 获取聊天窗口列表请求
     * @return 会话视图Map
     */
    @PostMapping("/getChatWindowList")
    public CommonResult<Map<String, ConversationViewVO>> getChatWindowList(@RequestBody InitConversationViewRequest request,
                                                                            @RequestHeader("X-User-Id") String agentId,
                                                                            @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服获取聊天窗口列表请求: agentId={}, businessLine={}", agentId, businessLine);
        
        try {
            Map<String, ConversationViewVO> response = agentHttpService.getChatWindowList(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取聊天窗口列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取聊天窗口列表失败", e);
        }
    }

    /**
     * 检查缺失的消息
     * 
     * @param request 检查缺失消息请求
     * @param agentId 客服ID
     * @return 缺失消息响应
     */
    @PostMapping("/checkMissingMessages")
    public CommonResult<CheckMissingMessagesResponse> checkMissingMessages(@RequestBody CheckMissingMessagesRequest request,
                                                                           @RequestHeader("X-User-Id") String agentId,
                                                                           @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服检查缺失消息请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
        try {
            CheckMissingMessagesResponse response = agentHttpService.checkMissingMessages(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服检查缺失消息失败: agentId={}", agentId, e);
            throw new RuntimeException("检查缺失消息失败", e);
        }
    }

    /**
     * 标记已读
     * 
     * @param request 标记已读请求
     * @param agentId 客服ID
     * @return 是否成功
     */
    @PostMapping("/markAsRead")
    public CommonResult<Boolean> markAsRead(@RequestBody MarkAsReadRequest request,
                                            @RequestHeader("X-User-Id") String agentId,
                                            @RequestHeader(value = "X-Business-Line", required = false) String businessLineHeader) {
        String businessLine = BusinessLineResolver.resolve(businessLineHeader);
        log.debug("处理客服标记已读请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
        try {
            boolean result = agentHttpService.markAsRead(request, agentId, businessLine);
            return CommonResult.buildSuccess(result);
        } catch (Exception e) {
            log.error("处理客服标记已读失败: agentId={}", agentId, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}