package com.treasurehunt.chat.controller;

import com.treasurehunt.chat.context.ChatRequestContext;
import com.treasurehunt.chat.httpservice.AgentHttpService;
import com.treasurehunt.chat.vo.*;
import com.treasurehuntshop.mall.common.api.commonUtil.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 客服聊天 HTTP 接口。业务线由 {@link com.treasurehunt.chat.config.ChatRequestContextInterceptor} 从登录 JWT 写入
 * {@link ChatRequestContext}。
 */
@Slf4j
@RestController
@RequestMapping("/chat/agent-service/conversation")
public class AgentHttpController {

    @Autowired
    private AgentHttpService agentHttpService;

    @PostMapping("/listConversations")
    public CommonResult<ActiveConversations> getConversations(@RequestBody AgentConversationsRequest request,
                                                              @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服获取会话列表请求: agentId={}, businessLine={}", agentId, businessLine);

        try {
            ActiveConversations response = agentHttpService.getConversations(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取会话列表失败", e);
        }
    }

    @PostMapping("/listParticipantConversations")
    public CommonResult<ActiveConversations> getParticipantConversations(@RequestBody AgentConversationsRequest request,
                                                                       @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服获取参与协作会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            ActiveConversations response = agentHttpService.getParticipantConversations(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取参与协作会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取参与协作会话列表失败", e);
        }
    }

    @PostMapping("/listCorporateConversations")
    public CommonResult<ActiveConversations> listCorporateConversations(
            @RequestBody AgentConversationsRequest request,
            @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理公司级会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            ActiveConversations response = agentHttpService.listCorporateConversations(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理公司级会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取公司级会话列表失败", e);
        }
    }

    @PostMapping("/listConversationsWithoutConfiguredAgentReception")
    public CommonResult<ActiveConversations> listConversationsWithoutConfiguredAgentReception(
            @RequestBody AgentConversationsRequest request,
            @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理未配置客服接待的会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            ActiveConversations response = agentHttpService
                    .listConversationsWithoutConfiguredAgentReception(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理未配置客服接待的会话列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取未配置客服接待的会话列表失败", e);
        }
    }

    @PostMapping("/joinConversation")
    public CommonResult<JoinConversationResult> joinConversation(@RequestBody JoinConversationRequest request,
                                                                 @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服加入会话请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);

        try {
            JoinConversationResult response = agentHttpService.grapConversation(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服加入会话失败: agentId={}", agentId, e);
            throw new RuntimeException("加入会话失败", e);
        }
    }

    @PostMapping("/pullMessageWithPagedQuery")
    public CommonResult<ChatmessageWithPaged> pullMessageWithPagedQuery(@RequestBody PullMessageWithPagedQueryRequest request,
                                                                         @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服分页拉取消息请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);

        try {
            ChatmessageWithPaged response = agentHttpService.pullMessageWithPagedQuery(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服分页拉取消息失败: agentId={}", agentId, e);
            throw new RuntimeException("分页拉取消息失败", e);
        }
    }

    @PostMapping("/deleteConversation")
    public CommonResult<String> deleteConversation(@RequestBody AgentDeleteConversationRequest request,
                                                   @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服删除会话请求: agentId={}, businessLine={}, conversationId={}", agentId, businessLine,
                request.getConversationId());

        try {
            String response = agentHttpService.deleteConversation(request.getConversationId(), agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服删除会话失败: agentId={}, conversationId={}", agentId, request.getConversationId(), e);
            throw new RuntimeException("删除会话失败", e);
        }
    }

    @PostMapping("/getChatWindowList")
    public CommonResult<Map<String, ConversationViewVO>> getChatWindowList(@RequestBody InitConversationViewRequest request,
                                                                            @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服获取聊天窗口列表请求: agentId={}, businessLine={}", agentId, businessLine);

        try {
            Map<String, ConversationViewVO> response = agentHttpService.getChatWindowList(agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服获取聊天窗口列表失败: agentId={}", agentId, e);
            throw new RuntimeException("获取聊天窗口列表失败", e);
        }
    }

    @PostMapping("/checkMissingMessages")
    public CommonResult<CheckMissingMessagesResponse> checkMissingMessages(@RequestBody CheckMissingMessagesRequest request,
                                                                           @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
        log.debug("处理客服检查缺失消息请求: agentId={}, businessLine={}, request={}", agentId, businessLine, request);

        try {
            CheckMissingMessagesResponse response = agentHttpService.checkMissingMessages(request, agentId, businessLine);
            return CommonResult.buildSuccess(response);
        } catch (Exception e) {
            log.error("处理客服检查缺失消息失败: agentId={}", agentId, e);
            throw new RuntimeException("检查缺失消息失败", e);
        }
    }

    @PostMapping("/markAsRead")
    public CommonResult<Boolean> markAsRead(@RequestBody MarkAsReadRequest request,
                                            @RequestHeader("X-User-Id") String agentId) {
        String businessLine = ChatRequestContext.getBusinessLine();
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
