package com.treasurehunt.chat.wsservice;

import com.treasurehunt.chat.component.manager.MessageIdGenerateResult;
import com.treasurehunt.chat.component.manager.MessageIdManager;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.enums.ConversationStatusEnum;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationDispatcher;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.component.routing.AgentRoutingService;
import com.treasurehunt.chat.component.routing.AgentRoutingService.RouteResult;
import com.treasurehunt.chat.component.cache.GroupMemberCacheManager;
import com.treasurehunt.chat.po.IdempotencyCheckResult;
import com.treasurehunt.chat.security.WebSocketRateLimiter;
import com.treasurehunt.chat.security.WebSocketSecurityFilter;
import com.treasurehunt.chat.service.IdempotencyService;
import com.treasurehunt.chat.service.UserContextService;
import com.treasurehunt.chat.service.RobotAgentService;
import com.treasurehunt.chat.component.async.ChatAsyncExecutor;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.treasurehunt.chat.utils.Conver;
import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.vo.ChatmessageWithPaged;
import com.treasurehunt.chat.vo.CheckMessageRequest;
import com.treasurehunt.chat.vo.PullMessageRequest;
import com.treasurehunt.chat.vo.PullMessageWithPagedQueryRequest;
import com.treasurehunt.chat.vo.ReplySendMessageResult;
import com.treasurehunt.chat.vo.WebSocketUserInfo;
import com.treasurehunt.chat.vo.HeartbeatRequest;
import com.treasurehunt.chat.vo.HeartbeatResponse;
import com.treasurehunt.chat.vo.HeartbeatItem;
import com.treasurehunt.chat.vo.HeartbeatResultItem;
import com.treasurehunt.chat.vo.CheckUnreadMessagesResponse;
import com.treasurehunt.chat.vo.ConversationInfo;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import com.treasurehunt.chat.mapper.UserConversationReadMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

/**
 * 客户聊天服务层
 * 负责处理客户相关的聊天业务逻辑
 */
@Slf4j
@Service
public class CustomerChatService {

    @Autowired
    private MessageIdManager messageIdManager;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private UserContextService userContextService;

    @Autowired
    private NotificationDispatcher notificationDispatcher;

    @Autowired
    private AgentRoutingService agentRoutingService;

    @Autowired
    private RobotAgentService robotAgentService;

    @Autowired
    private ChatConversationMapper conversationMapper;

    @Autowired
    private WebSocketRateLimiter rateLimiter;

    @Autowired
    private WebSocketSecurityFilter securityFilter;

    @Autowired
    private ChatAsyncExecutor chatAsyncExecutor;

    @Autowired
    private UserConversationReadMapper userConversationReadMapper;

    @Autowired
    private GroupMemberCacheManager groupMemberCacheManager;

    /**
     * 发送消息
     *
     * @param chatMessage 发送消息载荷
     * @param session     WebSocket会话
     * @return 发送结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReplySendMessageResult receiveMessage(ChatMessage chatMessage, WebSocketSession session) {
        log.info("接收客户端发送的消息: chatMessage={}", chatMessage);
        try {
            // 1. 从session中获取用户信息
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                throw new RuntimeException("会话中缺少用户信息");
            }

            // 1.5. 速率限制检查
            WebSocketRateLimiter.RateLimitResult rateLimitResult = rateLimiter.checkRateLimit(userInfo.getUserId());
            if (!rateLimitResult.isAllowed()) {
                log.warn("用户消息发送频率超限: userId={}, reason={}", userInfo.getUserId(), rateLimitResult.getReason());
                throw new RuntimeException("消息发送过于频繁，请稍后再试");
            }

            // 1.6. 安全过滤检查
            WebSocketSecurityFilter.SecurityCheckResult securityResult = securityFilter
                    .validateChatMessage(chatMessage);
            if (!securityResult.isValid()) {
                log.warn("消息安全验证失败: userId={}, reason={}", userInfo.getUserId(), securityResult.getReason());
                throw new RuntimeException("消息内容不符合安全规范: " + securityResult.getReason());
            }

            // 1.7. 过滤消息内容（清理恶意内容）
            String filteredContent = securityFilter.filterMessage(chatMessage.getContent());
            chatMessage.setContent(filteredContent);
            // 2.x 幂等性（优先 Redis，失败回退 MySQL）
            IdempotencyCheckResult idem = idempotencyService.checkBeforePersist(chatMessage.getConversationId(),
                    chatMessage.getClientMsgId());
            if (idem.isDuplicateFound()) {
                return new ReplySendMessageResult(chatMessage.getClientMsgId(),
                        chatMessage.getConversationId(), System.currentTimeMillis(), idem.getServerMsgId(), "PENDING");
            }
            // 3. 生成serverMsgId（从Redis获取，按会话递增，懒初始化）；若 Redis 不可用则进入降级模式
            MessageIdGenerateResult genResult = messageIdManager.generateServerMsgId(chatMessage.getConversationId());
            Long serverMsgId = genResult.getServerMsgId();
            log.info("生成serverMsgId: {}, redisAvailable={}", serverMsgId, genResult.isRedisAvailable());
            // 4. 插入消息到MySQL数据库（使用 Conver 进行转换）
            ChatMessageDO chatMessageDO = Conver.toChatMessageDO(chatMessage, serverMsgId);
            if (genResult.isRedisAvailable()) {
                try {
                    chatMessageMapper.insertOne(chatMessageDO);
                    idempotencyService.markSuccess(chatMessage.getConversationId(), chatMessage.getClientMsgId(),
                            serverMsgId);
                } catch (DuplicateKeyException e) {
                    // 极少数竞态：DB先前已有记录（例如并发绕过了Redis或PENDING TTL过短）
                    log.warn("数据库唯一索引冲突，查询已存在的记录: convId={}, clientMsgId={}",
                            chatMessage.getConversationId(), chatMessage.getClientMsgId());
                    Long existedServerMsgId = idempotencyService.handleDuplicateKeyConflict(
                            chatMessage.getConversationId(), chatMessage.getClientMsgId());
                    if (existedServerMsgId != null) {
                        // 找到了已存在的记录，返回该结果（幂等成功）
                        updateHasReadToLatest(chatMessage.getConversationId(), userInfo.getUserId(),
                                existedServerMsgId);
                        return new ReplySendMessageResult(chatMessage.getClientMsgId(),
                                chatMessage.getConversationId(), System.currentTimeMillis(), existedServerMsgId,
                                "PENDING");
                    }
                    // 理论上不会到这里，但为了安全还是抛出异常
                    throw e;
                }
            } else {
                // 降级路径：利用 ON DUPLICATE KEY 自增 server_msg_id，牺牲幂等性
                chatMessageMapper.insertOneOnDupIncrAnno(chatMessageDO);
            }

            // 4.5. 自动更新发送者的已读记录到最新位置（在核心事务中）
            updateHasReadToLatest(chatMessage.getConversationId(), userInfo.getUserId(), serverMsgId);

            // 5. 返回成功结果（核心事务结束）
            ReplySendMessageResult result = new ReplySendMessageResult(chatMessage.getClientMsgId(),
                    chatMessage.getConversationId(), System.currentTimeMillis(), serverMsgId, "PENDING");

            // 6. 异步处理会话状态和分配逻辑（移出事务）
            chatAsyncExecutor.executeConversationTask(
                    () -> handleConversation(chatMessage.getConversationId(), serverMsgId, session,
                            chatMessage.getShopId()),
                    "处理客户会话",
                    chatMessage.getConversationId(), serverMsgId);

            return result;
        } catch (Exception e) {
            log.error("客户发送消息失败", e);
            throw new RuntimeException("客户发送消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重连补齐：查询会话内所有大于 lastServerMsgId 的消息，按 server_msg_id 升序返回
     */
    public List<ChatMessage> checkReconnectMessages(CheckMessageRequest request, WebSocketSession session) {
        log.info("客户重连补齐: request={}", request);
        try {
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                throw new RuntimeException("会话中缺少用户信息");
            }
            if (request == null || request.getConversationId() == null || request.getConversationId().isEmpty()) {
                throw new RuntimeException("conversationId不能为空");
            }
            long fromMsgId = request.getServerMsgId() == null ? 0L : request.getServerMsgId();
            QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", request.getConversationId())
                    .gt("server_msg_id", fromMsgId)
                    .orderByAsc("server_msg_id");
            List<ChatMessageDO> rows = chatMessageMapper.selectList(queryWrapper);
            return rows.stream().map(Conver::toChatMessage).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("客户重连补齐失败", e);
            throw new RuntimeException("客户重连补齐失败: " + e.getMessage(), e);
        }
    }

    /**
     * 客户端兜底心跳：批量上报各会话 clientMaxServerMsgId，用于是否需要拉取的对账。
     * 不参与 WS 会话 TTL 续期（续期由切面在 handleMessage 中统一完成）。
     */
    public HeartbeatResponse heartbeat(HeartbeatRequest heartbeatRequest, WebSocketSession session) {
        try {
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                throw new RuntimeException("会话中缺少用户信息");
            }

            List<HeartbeatResultItem> resultItems = new ArrayList<>();
            if (heartbeatRequest != null && heartbeatRequest.getItems() != null) {
                // 批量查询所有会话的最新 serverMsgId，然后内存中对比
                Map<String, Long> latestServerMsgIdMap = batchProbeLatestServerMsgIds(heartbeatRequest.getItems());
                for (HeartbeatItem item : heartbeatRequest.getItems()) {
                    if (item == null || item.getConversationId() == null)
                        continue;
                    long clientMax = item.getClientMaxServerMsgId() == null ? 0L : item.getClientMaxServerMsgId();
                    Long latest = latestServerMsgIdMap.get(item.getConversationId());
                    if (latest == null) {
                        latest = 0L;
                    }
                    boolean needPull = latest > clientMax;
                    Long pullFrom = needPull ? (clientMax + 1) : null;
                    resultItems.add(HeartbeatResultItem.builder()
                            .conversationId(item.getConversationId())
                            .needPull(needPull)
                            .latestServerMsgId(latest)
                            .pullFrom(pullFrom)
                            .build());
                }
            }

            return HeartbeatResponse.builder()
                    .results(resultItems)
                    .serverTime(System.currentTimeMillis())
                    .build();
        } catch (Exception e) {
            log.error("心跳处理失败", e);
            throw new RuntimeException("心跳处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 批量探测各会话的最新 serverMsgId（仅查询数据库，无缓存逻辑）
     */
    private Map<String, Long> batchProbeLatestServerMsgIds(List<HeartbeatItem> items) {
        Map<String, Long> result = new HashMap<>();
        List<String> conversationIds = items.stream()
                .filter(item -> item != null && item.getConversationId() != null)
                .map(HeartbeatItem::getConversationId)
                .distinct()
                .collect(Collectors.toList());
        if (conversationIds.isEmpty()) {
            return result;
        }
        try {
            List<Map<String, Object>> dbResults = chatMessageMapper.getMaxServerMsgIdBatch(conversationIds);
            for (Map<String, Object> row : dbResults) {
                String conversationId = (String) row.get("conversation_id");
                Object serverMsgId = row.get("server_msg_id");
                Long msgId = serverMsgId != null ? ((Number) serverMsgId).longValue() : 0L;
                result.put(conversationId, msgId);
            }
        } catch (Exception e) {
            log.error("批量查询数据库失败: conversationIds={}", conversationIds, e);
            throw new RuntimeException("批量查询数据库失败");
        }
        return result;
    }

    /**
     * 拉取消息
     *
     * @param pullMessageRequest 拉取请求载荷
     * @param session            WebSocket会话
     * @return 消息列表
     */
    public PullMessageRequest pullMessage(PullMessageRequest pullMessageRequest, WebSocketSession session) {
        log.info("客户拉取消息: pullMessageRequest={}", pullMessageRequest);
        try {
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                throw new RuntimeException("会话中缺少用户信息");
            }

            String conversationId = pullMessageRequest.getConversationId();
            Long serverMsgId = pullMessageRequest.getServerMsgId();

            if (conversationId == null || serverMsgId == null) {
                throw new RuntimeException("会话ID和消息ID不能为空");
            }

            // 查询指定消息
            QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", conversationId)
                    .eq("server_msg_id", serverMsgId);

            ChatMessageDO messageDO = chatMessageMapper.selectOne(queryWrapper);

            if (messageDO == null) {
                log.warn("未找到指定消息: conversationId={}, serverMsgId={}", conversationId, serverMsgId);
                // 返回空消息的响应
                return PullMessageRequest.builder()
                        .conversationId(conversationId)
                        .type(pullMessageRequest.getType())
                        .timestamp(System.currentTimeMillis())
                        .serverMsgId(serverMsgId)
                        .message(null) // 消息不存在
                        .build();
            }

            // 转换为VO对象
            ChatMessage message = Conver.toChatMessage(messageDO);

            // 返回包含消息的响应
            return PullMessageRequest.builder()
                    .conversationId(conversationId)
                    .type(pullMessageRequest.getType())
                    .timestamp(System.currentTimeMillis())
                    .serverMsgId(serverMsgId)
                    .message(message)
                    .build();

        } catch (Exception e) {
            log.error("客户拉取消息失败", e);
            throw new RuntimeException("客户拉取消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 分页拉取消息
     *
     * @param request 分页拉取请求载荷
     * @param session WebSocket会话
     * @return 分页消息结果
     */
    public ChatmessageWithPaged pullMessageWithPagedQuery(PullMessageWithPagedQueryRequest request,
            WebSocketSession session) {
        log.info("客户分页拉取消息: request={}", request);
        try {
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                throw new RuntimeException("会话中缺少用户信息");
            }
            // 分页查询
            QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", request.getConversationId())
                    .orderByDesc("created_at")
                    .last("limit " + request.getPageSize() + " offset "
                            + (request.getCurrentPage() - 1) * request.getPageSize());
            List<ChatMessageDO> chatMessages = chatMessageMapper.selectList(queryWrapper);
            List<ChatMessage> chatMessagesVO = chatMessages.stream().map(Conver::toChatMessage)
                    .collect(Collectors.toList());
            return ChatmessageWithPaged.builder()
                    .conversationId(request.getConversationId())
                    .currentPage(request.getCurrentPage())
                    .chatMessages(chatMessagesVO)
                    .build();
        } catch (Exception e) {
            log.error("客户分页拉取消息失败", e);
            throw new RuntimeException("客户分页拉取消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理会话状态和分配逻辑
     *
     * @param conversationId 会话ID
     * @param serverMsgId    消息ID
     * @param session        WebSocket会话
     * @param shopId         店铺ID
     */
    private void handleConversation(String conversationId, Long serverMsgId, WebSocketSession session, Long shopId) {
        log.debug("🔍 开始处理客户会话: conversationId={}, serverMsgId={}, shopId={}", conversationId, serverMsgId, shopId);
        try {
            // 1. 检查会话是否已存在
            log.debug("📋 检查会话是否已存在: conversationId={}", conversationId);
            QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", conversationId);
            ChatConversationDO existingConversation = conversationMapper.selectOne(queryWrapper);

            if (existingConversation != null) {
                log.debug("✅ 会话已存在: conversationId={}, status={}", conversationId, existingConversation.getStatus());
                // 会话已存在 - 检查是否需要激活状态并推送给现有客服
                ConversationStatusEnum currentStatus = ConversationStatusEnum
                        .fromCode(existingConversation.getStatus());
                boolean needActivation = currentStatus != null && !currentStatus.isActive();
                log.debug("🔄 会话激活检查: conversationId={}, needActivation={}", conversationId, needActivation);
                activateExistingConversation(conversationId, serverMsgId, needActivation, shopId);
            } else {
                log.debug("🆕 会话不存在，需要创建新会话: conversationId={}", conversationId);
                // 会话不存在 - 创建新会话并分配
                createAndAssignNewConversation(conversationId, serverMsgId, session, shopId);
            }
            log.debug("✅ 客户会话处理完成: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("❌ 处理会话状态失败: conversationId={}", conversationId, e);
            // 后续加入告警机制
        }
    }

    /**
     * 处理已存在的会话（客户发送消息时）
     *
     * @param conversationId 会话ID
     * @param serverMsgId    服务端消息ID
     * @param needActivation 是否需要激活会话状态（从waiting/closed等状态激活为active）
     */
    private void activateExistingConversation(String conversationId, Long serverMsgId, boolean needActivation,
            Long shopId) {
        log.debug("🔄 开始处理已存在会话: conversationId={}, serverMsgId={}, needActivation={}", conversationId, serverMsgId,
                needActivation);
        // 如果需要激活，更新会话状态为active
        if (needActivation) {
            log.debug("🔄 需要激活会话状态: conversationId={}", conversationId);
            try {
                UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("conversation_id", conversationId)
                        .set("status", ConversationStatusEnum.ACTIVE.getCode())
                        .set("updated_at", new Date());
                int updateResult = conversationMapper.update(null, updateWrapper);
                if (updateResult > 0) {
                    log.debug("✅ 会话状态已激活: conversationId={}, status=active", conversationId);
                } else {
                    log.warn("⚠️ 会话状态激活失败: conversationId={}", conversationId);
                }
            } catch (Exception e) {
                log.error("❌ 激活会话状态失败: conversationId={}", conversationId, e);
            }
        } else {
            log.debug("ℹ️ 会话状态无需激活: conversationId={}", conversationId);
        }
        // 已存在会话：使用统一的消息分发接口
        // 需要获取发送者ID（客户ID）
        log.debug("获取客户会话ID: conversationId={}", conversationId);
        String customerId = getCustomerIdByConversationId(conversationId);
        // 当前类是处理客户发送过来的消息，如果整个群异常了,连客户自己都不在群里了,需要重新分配客服
        List<ChatConversationMemberDO> members = groupMemberCacheManager.getGroupMembers(conversationId);
        Set<String> memberIds = members.stream().map(ChatConversationMemberDO::getMemberId).collect(Collectors.toSet());
        if (members.isEmpty()) {
            log.debug("🎯 开始分配客服: conversationId={}, customerId={}", conversationId, customerId);
            String assignedAgentId = agentRoutingService.assignAgents(conversationId, false).agentId;
            if (assignedAgentId != null) {
                memberIds = Collections.singleton(assignedAgentId);
            }
        }
        // 如果群里只剩下客户自己和机器人,就重新分配客服
        Boolean isOnlyCustomerAndRobot = members.size() == 2 && members.stream().anyMatch(m -> m.getMemberId().equals(customerId)) && members.stream().anyMatch(m -> m.getMemberType().equals("robot_agent"));
        if (isOnlyCustomerAndRobot) {
            log.debug("🎯 群里只剩下客户自己和机器人,重新分配客服: conversationId={}", conversationId);
            String assignedAgentId = agentRoutingService.assignAgents(conversationId, false).agentId;
            if (assignedAgentId != null) {
                memberIds = Collections.singleton(assignedAgentId);
            }
        }
        log.debug("🎯 客服分配结果: conversationId={}, memberIds={}", conversationId, memberIds);
        log.debug("📤 推送消息给客服: conversationId={}, serverMsgId={}", conversationId, serverMsgId);
        notificationDispatcher.dispatch(conversationId, serverMsgId, customerId, memberIds);
        // 同时抄送给机器人
        log.debug("🤖 同时抄送给机器人: conversationId={}", conversationId);
        robotAgentService.sendAutoReplyMessage(conversationId, serverMsgId, customerId, shopId);
        log.debug("✅ 已存在会话处理完成: conversationId={}", conversationId);
    }

    /**
     * 根据会话ID获取客户ID
     *
     * @param conversationId 会话ID
     * @return 客户ID
     */
    private String getCustomerIdByConversationId(String conversationId) {
        try {
            QueryWrapper<ChatConversationDO> query = new QueryWrapper<>();
            query.eq("conversation_id", conversationId);
            ChatConversationDO conversation = conversationMapper.selectOne(query);
            return conversation != null ? String.valueOf(conversation.getCustomerId()) : null;
        } catch (Exception e) {
            log.error("Failed to get customer ID by conversation ID: {}", conversationId, e);
            return null;
        }
    }

    /**
     * 创建新会话并分配
     */
    private void createAndAssignNewConversation(String conversationId, Long serverMsgId, WebSocketSession session,
            Long shopId) {
        log.debug("🆕 开始创建新会话: conversationId={}, serverMsgId={}, shopId={}", conversationId, serverMsgId, shopId);
        // 从WebSocketUserInfo中获取真实客户信息
        WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
        if (userInfo == null) {
            log.error("❌ 创建新会话失败：无法获取用户信息, conversationId={}", conversationId);
            return;
        }
        log.debug("👤 获取到用户信息: conversationId={}, userId={}", conversationId, userInfo.getUserId());
        log.debug("🎯 开始分配客服: conversationId={}", conversationId);
        RouteResult routeResult = agentRoutingService.assignAgents(conversationId, true);
        log.debug("🎯 客服分配结果: conversationId={}, agentId={}", conversationId, routeResult.agentId);
        // 创建会话记录
        log.debug("📝 准备创建会话记录: conversationId={}, customerId={}, shopId={}", conversationId, userInfo.getUserId(),shopId);
        ChatConversationDO conversation = new ChatConversationDO();
        conversation.setConversationId(conversationId);
        conversation.setCustomerId(userInfo.getUserId());
        conversation.setStatus(ConversationStatusEnum.WAITING.getCode());
        conversation.setTenantId(1L); // 默认租户
        conversation.setShopId(shopId);
        conversation.setAgentId(routeResult.agentId);
        conversation.setCreatedAt(new Date());
        conversation.setUpdatedAt(new Date());
        try {
            conversationMapper.insert(conversation);
            log.debug("✅ 成功创建会话记录: conversationId={}, customerId={}, shopId={}",
                    conversationId, userInfo.getUserId(), shopId);
        } catch (Exception e) {
            log.error("❌ 创建会话记录失败: conversationId={}", conversationId, e);
            return;
        }
        // 创建群聊成员记录（批量添加客户和客服）
        List<ChatConversationMemberDO> members = new ArrayList<>();
        if (routeResult.agentId != null) {
            members.addAll(Conver.toGroupMembers(Collections.singletonList(routeResult.agentId), conversationId, "agent"));
        }
        members.addAll(Conver.toGroupMembers(Collections.singletonList(userInfo.getUserId()), conversationId, "customer"));
        // 把机器人也加进去,暂定它的id为666666
        members.addAll(Conver.toGroupMembers(Collections.singletonList("666666"), conversationId, "robot_agent"));
        if (!members.isEmpty()) {
            groupMemberCacheManager.addGroupMembers(conversationId, members);
            log.debug("✅ 批量添加群聊成员成功: conversationId={}, 成员数量={}", conversationId, members.size());
        }
        
        log.debug("📤 推送消息给客服: conversationId={}, serverMsgId={}", conversationId, serverMsgId);
        // 新会话创建：使用统一的消息分发接口
        Set<String> agentIdsForDispatch = routeResult.agentId != null ? Collections.singleton(routeResult.agentId) : Collections.emptySet();
        notificationDispatcher.dispatch(conversationId, serverMsgId, userInfo.getUserId(), agentIdsForDispatch);
        // 同时抄送给机器人
        log.debug("🤖 同时抄送给机器人: conversationId={}", conversationId);
        robotAgentService.sendAutoReplyMessage(conversationId, serverMsgId, userInfo.getUserId(), shopId);
        log.debug("✅ 新会话创建完成: conversationId={}", conversationId);
    }

    /**
     * TODO 未来可能实现,重新开放会话供客服抢单,目前这种场景是先分配给售前客服
     * 当群聊里只剩下机器人时，需要重新开放抢单,机器人是永远不退群的,哪怕客户删除了聊天窗口
     */
    private void reopenConversationForGrabbing(String conversationId, Long serverMsgId) {
        try {
            log.info("重新开放会话供客服抢单: conversationId={}", conversationId);
            // 1. 更新会话状态为waiting（等待客服抢单）
            UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("conversation_id", conversationId)
                    .set("status", ConversationStatusEnum.WAITING.getCode())
                    .set("updated_at", new Date());

            int updateResult = conversationMapper.update(null, updateWrapper);
            if (updateResult > 0) {
                log.info("会话状态已更新为waiting，等待客服抢单: conversationId={}", conversationId);
            } else {
                log.warn("更新会话状态失败: conversationId={}", conversationId);
            }
        } catch (Exception e) {
            log.error("重新开放会话抢单失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 自动更新发送者的已读记录到最新位置（发送消息时调用）
     * 
     * @param conversationId 会话ID
     * @param userId         用户ID
     * @param serverMsgId    服务端消息ID
     */
    private void updateHasReadToLatest(String conversationId, String userId, Long serverMsgId) {
        log.debug("自动更新发送者已读记录: conversationId={}, userId={}, serverMsgId={}",
                conversationId, userId, serverMsgId);

        try {
            // 查询当前已读指针（userId是String，转换为Long）
            Long userIdLong = Long.parseLong(userId);
            UserConversationReadDO existingRecord = userConversationReadMapper.selectOne(
                    new QueryWrapper<UserConversationReadDO>()
                            .eq("conversation_id", conversationId)
                            .eq("user_id", userIdLong));

            if (existingRecord != null) {
                // 更新已存在的记录，使用乐观锁防止回退
                if (serverMsgId > existingRecord.getLastReadServerMsgId()) {
                    UpdateWrapper<UserConversationReadDO> updateWrapper = new UpdateWrapper<>();
                    updateWrapper.eq("conversation_id", conversationId)
                            .eq("user_id", userIdLong)
                            .eq("last_read_server_msg_id", existingRecord.getLastReadServerMsgId()) // 乐观锁条件
                            .set("last_read_server_msg_id", serverMsgId)
                            .set("updated_at", new Date());

                    int updateResult = userConversationReadMapper.update(null, updateWrapper);
                    if (updateResult > 0) {
                        log.debug("发送者已读指针更新成功: conversationId={}, userId={}, serverMsgId={}",
                                conversationId, userId, serverMsgId);
                    } else {
                        log.warn("发送者已读指针更新失败，可能被其他设备更新: conversationId={}, userId={}",
                                conversationId, userId);
                    }
                } else {
                    log.debug("发送者已读指针无需更新，当前值已大于等于新值: conversationId={}, userId={}, current={}, new={}",
                            conversationId, userId, existingRecord.getLastReadServerMsgId(), serverMsgId);
                }
            } else {
                // 插入新记录
                UserConversationReadDO newRecord = new UserConversationReadDO();
                newRecord.setConversationId(conversationId);
                newRecord.setUserId(userIdLong);
                newRecord.setLastReadServerMsgId(serverMsgId);
                newRecord.setUpdatedAt(new Date());

                int insertResult = userConversationReadMapper.insert(newRecord);
                if (insertResult > 0) {
                    log.debug("发送者已读指针插入成功: conversationId={}, userId={}, serverMsgId={}",
                            conversationId, userId, serverMsgId);
                } else {
                    log.error("发送者已读指针插入失败: conversationId={}, userId={}", conversationId, userId);
                    throw new RuntimeException("发送者已读指针插入失败");
                }
            }

        } catch (Exception e) {
            log.error("自动更新发送者已读记录失败: conversationId={}, userId={}, serverMsgId={}",
                    conversationId, userId, serverMsgId, e);
            throw new RuntimeException("自动更新发送者已读记录失败", e);
        }
    }

    /**
     * 检查未读消息
     * 
     * @param userId 用户ID
     * @return 未读消息检查结果
     */
    public CheckUnreadMessagesResponse checkUnreadMessages(Long userId) {
        log.debug("开始检查用户未读消息: userId={}", userId);

        try {
            // 1. 查询用户参与的所有会话
            List<ChatConversationDO> conversations = conversationMapper.selectList(
                    new QueryWrapper<ChatConversationDO>()
                            .eq("customer_id", userId)
                            .in("status", "active", "waiting")
                            .orderByDesc("updated_at"));

            if (conversations.isEmpty()) {
                log.debug("用户没有参与任何会话: userId={}", userId);
                return CheckUnreadMessagesResponse.builder()
                        .hasUnreadMessages(false)
                        .totalUnreadCount(0)
                        .conversationUnreadCounts(new HashMap<>())
                        .conversations(new ArrayList<>())
                        .build();
            }

            // 2. 批量获取会话信息（包含最后一条消息和未读消息数）
            List<ConversationInfo> conversationInfos = getConversationInfos(conversations, String.valueOf(userId));

            // 3. 计算总未读消息数和各窗口未读数
            Integer totalUnreadCount = conversationInfos.stream()
                    .mapToInt(ConversationInfo::getUnreadCount)
                    .sum();

            // 4. 构建各窗口未读数映射
            Map<String, Integer> conversationUnreadCounts = conversationInfos.stream()
                    .collect(Collectors.toMap(
                            ConversationInfo::getConversationId,
                            ConversationInfo::getUnreadCount,
                            (existing, replacement) -> existing // 处理重复key的情况
                    ));

            boolean hasUnreadMessages = totalUnreadCount > 0;

            log.debug("未读消息检查完成: userId={}, hasUnreadMessages={}, totalUnreadCount={}",
                    userId, hasUnreadMessages, totalUnreadCount);

            return CheckUnreadMessagesResponse.builder()
                    .hasUnreadMessages(hasUnreadMessages)
                    .totalUnreadCount(totalUnreadCount)
                    .conversationUnreadCounts(conversationUnreadCounts)
                    .conversations(conversationInfos)
                    .build();

        } catch (Exception e) {
            log.error("检查未读消息失败: userId={}", userId, e);
            throw new RuntimeException("检查未读消息失败", e);
        }
    }

    /**
     * 批量获取会话信息（包含最后一条消息和未读消息数）
     * 复用AgentServiceChatService中的逻辑
     * 
     * @param conversations 会话列表
     * @param userId        用户ID（用于计算未读消息数）
     * @return 会话信息列表
     */
    private List<ConversationInfo> getConversationInfos(List<ChatConversationDO> conversations, String userId) {
        if (conversations.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> conversationIds = conversations.stream()
                .map(ChatConversationDO::getConversationId)
                .collect(Collectors.toList());

        // 1. 批量获取每个会话的最后一条消息
        List<ChatMessageDO> lastMessages = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessageDO>()
                        .in("conversation_id", conversationIds)
                        .inSql("server_msg_id",
                                "SELECT MAX(server_msg_id) FROM chat_message cm2 " +
                                        "WHERE cm2.conversation_id = chat_message.conversation_id"));

        // 2. 构建消息映射
        Map<String, ChatMessageDO> messageMap = lastMessages.stream()
                .collect(Collectors.toMap(
                        ChatMessageDO::getConversationId,
                        message -> message,
                        (existing, replacement) -> existing));

        // 3. 批量查询未读消息数（如果提供了userId）
        Map<String, Integer> unreadCountMap = new HashMap<>();
        if (userId != null) {
            // 批量查询已读指针（userId是String，转换为Long）
            List<UserConversationReadDO> readRecords = userConversationReadMapper.selectList(
                    new QueryWrapper<UserConversationReadDO>()
                            .in("conversation_id", conversationIds)
                            .eq("user_id", Long.parseLong(userId)));

            Map<String, Long> readPointerMap = readRecords.stream()
                    .collect(Collectors.toMap(
                            UserConversationReadDO::getConversationId,
                            UserConversationReadDO::getLastReadServerMsgId));

            // 计算每个会话的未读消息数
            for (ChatMessageDO message : lastMessages) {
                String convId = message.getConversationId();
                Long lastReadServerMsgId = readPointerMap.getOrDefault(convId, 0L);
                long unreadCount = message.getServerMsgId() - lastReadServerMsgId;
                unreadCountMap.put(convId, Math.max(0, (int) unreadCount));
            }
        }

        // 4. 批量转换为VO
        return conversations.stream()
                .map(conv -> {
                    ChatMessageDO lastMessage = messageMap.get(conv.getConversationId());
                    Integer unreadCount = unreadCountMap.getOrDefault(conv.getConversationId(), 0);

                    return Conver.toConversationInfo(
                            conv,
                            lastMessage != null ? lastMessage.getContent() : null,
                            lastMessage != null ? lastMessage.getCreatedAt().getTime() : null,
                            unreadCount);
                })
                .collect(Collectors.toList());
    }
}
