package com.treasurehunt.chat.controller;

import com.treasurehunt.chat.httpservice.AgentHttpService;
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
    public CommonResult<ActiveConversations> getConversations(@RequestBody AgentConversationsRequest request) {
        log.debug("处理客服获取会话列表请求: agentId={}", request.getAgentId());
        
        try {
            ActiveConversations response = agentHttpService.getConversations(request.getAgentId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服获取会话列表失败: agentId={}", request.getAgentId(), e);
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
    public CommonResult<GetUnassignedConversationsResult> getUnassignedConversations(@RequestBody AgentConversationsRequest request) {
        log.debug("处理客服获取待分配会话列表请求: agentId={}", request.getAgentId());
        
        try {
            GetUnassignedConversationsResult response = agentHttpService.getUnassignedConversations(request.getAgentId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服获取待分配会话列表失败: agentId={}", request.getAgentId(), e);
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
    public CommonResult<JoinConversationResult> joinConversation(@RequestBody JoinConversationRequest request) {
        log.debug("处理客服加入会话请求: agentId={}, request={}", request.getAgentId(), request);
        
        try {
            JoinConversationResult response = agentHttpService.grapConversation(request, request.getAgentId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服加入会话失败: agentId={}", request.getAgentId(), e);
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
    public CommonResult<ChatmessageWithPaged> pullMessageWithPagedQuery(@RequestBody PullMessageWithPagedQueryRequest request) {
        log.debug("处理客服分页拉取消息请求: agentId={}, request={}", request.getAgentId(), request);
        
        try {
            ChatmessageWithPaged response = agentHttpService.pullMessageWithPagedQuery(request, request.getAgentId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服分页拉取消息失败: agentId={}", request.getAgentId(), e);
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
    public CommonResult<String> deleteConversation(@RequestBody AgentDeleteConversationRequest request) {
        log.debug("处理客服删除会话请求: agentId={}, conversationId={}", request.getAgentId(), request.getConversationId());
        
        try {
            String response = agentHttpService.deleteConversation(request.getConversationId(), request.getAgentId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服删除会话失败: agentId={}, conversationId={}", request.getAgentId(), request.getConversationId(), e);
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
    public CommonResult<Map<String, ConversationViewVO>> getChatWindowList(@RequestBody InitConversationViewRequest request) {
        log.debug("处理客服获取聊天窗口列表请求: agentId={}", request.getUserId());
        
        try {
            Map<String, ConversationViewVO> response = agentHttpService.getChatWindowList(request.getUserId());
            return CommonResult.success(response);
        } catch (Exception e) {
            log.error("处理客服获取聊天窗口列表失败: agentId={}", request.getUserId(), e);
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
    public CommonResult<CheckMissingMessagesResponse> checkMissingMessages(@RequestBody CheckMissingMessagesRequest request, @RequestHeader("X-User-Id") String agentId) {
        log.debug("处理客服检查缺失消息请求: agentId={}, request={}", agentId, request);
        
        try {
            CheckMissingMessagesResponse response = agentHttpService.checkMissingMessages(request, agentId);
            return CommonResult.success(response);
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
    public CommonResult<Boolean> markAsRead(@RequestBody MarkAsReadRequest request, @RequestHeader("X-User-Id") String agentId) {
        log.debug("处理客服标记已读请求: agentId={}, request={}", agentId, request);
        
        try {
            boolean result = agentHttpService.markAsRead(request, agentId);
            return CommonResult.success(result);
        } catch (Exception e) {
            log.error("处理客服标记已读失败: agentId={}", agentId, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}