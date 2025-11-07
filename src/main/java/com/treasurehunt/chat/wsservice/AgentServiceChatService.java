package com.treasurehunt.chat.wsservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.treasurehunt.chat.component.manager.MessageIdGenerateResult;
import com.treasurehunt.chat.component.manager.MessageIdManager;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import com.treasurehunt.chat.enums.ConversationStatusEnum;
import com.treasurehunt.chat.framework.core.websocket.distributed.delivery.NotificationDispatcher;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.UserConversationReadMapper;
import com.treasurehunt.chat.component.cache.GroupMemberCacheManager;
import com.treasurehunt.chat.po.IdempotencyCheckResult;
import com.treasurehunt.chat.security.WebSocketRateLimiter;
import com.treasurehunt.chat.security.WebSocketSecurityFilter;
import com.treasurehunt.chat.service.IdempotencyService;
import com.treasurehunt.chat.service.UserContextService;
import com.treasurehunt.chat.utils.Conver;
import com.treasurehunt.chat.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

import com.treasurehunt.chat.component.async.ChatAsyncExecutor;
import com.treasurehuntshop.mall.common.exception.ApiException;
import com.treasurehuntshop.mall.common.enums.BusinessErrorCodeEnum;


import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;

/**
 * 客服聊天服务层
 * 负责处理客服相关的聊天业务逻辑
 */
@Slf4j
@Service
public class AgentServiceChatService {

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
    private ChatConversationMapper conversationMapper;

    @Autowired
    private GroupMemberCacheManager groupMemberCacheManager;

    @Autowired
    private UserConversationReadMapper userConversationReadMapper;

    @Autowired
    private WebSocketRateLimiter rateLimiter;

    @Autowired
    private WebSocketSecurityFilter securityFilter;
    
    @Autowired
    private ChatAsyncExecutor chatAsyncExecutor;



    /**
     * 接收客户端请求消息
     * 
     * @param chatMessage 消息载荷
     * @param session            WebSocket会话
     * @return 发送结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ReplySendMessageResult receiveMessage(ChatMessage chatMessage, WebSocketSession session) {
        log.info("接收客服发送的消息: chatMessage={}", chatMessage);
        try {
            // 1. 从session中获取用户信息（网关透传）
            WebSocketUserInfo userInfo = userContextService.getUserInfo(session);
            if (userInfo == null) {
                return userContextService.buildMissingUserResponse(chatMessage);
            }
            
            // 1.5. 速率限制检查
            WebSocketRateLimiter.RateLimitResult rateLimitResult = rateLimiter.checkRateLimit(userInfo.getUserId());
            if (!rateLimitResult.isAllowed()) {
                log.warn("客服消息发送频率超限: userId={}, reason={}", userInfo.getUserId(), rateLimitResult.getReason());
                throw new RuntimeException("消息发送过于频繁，请稍后再试");
            }
            
            // 1.6. 安全过滤检查
            WebSocketSecurityFilter.SecurityCheckResult securityResult = securityFilter.validateChatMessage(chatMessage);
            if (!securityResult.isValid()) {
                log.warn("客服消息安全验证失败: userId={}, reason={}", userInfo.getUserId(), securityResult.getReason());
                throw new RuntimeException("消息内容不符合安全规范: " + securityResult.getReason());
            }
            
            // 1.7. 过滤消息内容（清理恶意内容）
            String filteredContent = securityFilter.filterMessage(chatMessage.getContent());
            chatMessage.setContent(filteredContent);
            // 2.x 幂等性与降级（优先 Redis，失败回退 MySQL）
            IdempotencyCheckResult idem = idempotencyService.checkBeforePersist(chatMessage.getConversationId(), chatMessage.getClientMsgId());
            if (idem.isDuplicateFound()) {
                ReplySendMessageResult resultPayload = new ReplySendMessageResult(chatMessage.getClientMsgId(),chatMessage.getConversationId(),System.currentTimeMillis(), idem.getServerMsgId(), "PENDING");
                return resultPayload;
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
                    idempotencyService.markSuccess(chatMessage.getConversationId(), chatMessage.getClientMsgId(), serverMsgId);
                } catch (DuplicateKeyException e) {
                    // 极少数竞态：DB先前已有记录（例如并发绕过了Redis或PENDING TTL过短）
                    log.warn("数据库唯一索引冲突，查询已存在的记录: convId={}, clientMsgId={}", 
                            chatMessage.getConversationId(), chatMessage.getClientMsgId());
                    Long existedServerMsgId = idempotencyService.handleDuplicateKeyConflict(
                            chatMessage.getConversationId(), chatMessage.getClientMsgId());
                    if (existedServerMsgId != null) {
                        // 找到了已存在的记录，返回该结果（幂等成功）
                        updateHasReadToLatest(chatMessage.getConversationId(), userInfo.getUserId(), existedServerMsgId);
                        ReplySendMessageResult resultPayload = new ReplySendMessageResult(chatMessage.getClientMsgId(),
                                chatMessage.getConversationId(), System.currentTimeMillis(), existedServerMsgId, "PENDING");
                        return resultPayload;
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
            ReplySendMessageResult resultPayload = new ReplySendMessageResult(chatMessage.getClientMsgId(),chatMessage.getConversationId(), System.currentTimeMillis(), serverMsgId, "PENDING");
            
            // 6. 异步处理会话状态和分配逻辑（移出事务）
            chatAsyncExecutor.executeConversationTask(
                () -> handleConversation(chatMessage.getConversationId(), serverMsgId, userInfo.getUserId()),
                "处理客服会话",
                chatMessage.getConversationId(), serverMsgId
            );
            
            return resultPayload;
        } catch (Exception e) {
            log.error("客服发送消息失败", e);
            // 异常必须抛出，不能包裹成失败响应，否则前端会以为是正常的
            throw new RuntimeException("客服发送消息失败: " + e.getMessage(), e);
        }
    }

    public PullMessageRequest pullMessage(PullMessageRequest pullMessageRequest, WebSocketSession session) {
        log.info("客服拉取消息: payload={}", pullMessageRequest);
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
            log.error("客服拉取消息失败", e);
            throw new RuntimeException("客服拉取消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重连补齐：客服侧查询会话内所有大于 lastServerMsgId 的消息，按 server_msg_id 升序返回
     */
    public List<ChatMessage> checkReconnectMessages(CheckMessageRequest request, WebSocketSession session) {
        log.info("客服重连补齐: request={}", request);
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
            log.error("客服重连补齐失败", e);
            throw new RuntimeException("客服重连补齐失败: " + e.getMessage(), e);
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
                // TODO: 数据库和缓存一致性比较复杂，暂时去掉缓存逻辑，后续需要一致性方案时再实现
                // 批量查询所有会话的最新 serverMsgId，然后内存中对比
                Map<String, Long> latestServerMsgIdMap = batchProbeLatestServerMsgIds(heartbeatRequest.getItems());
                
                for (HeartbeatItem item : heartbeatRequest.getItems()) {
                    if (item == null || item.getConversationId() == null) continue;
                    long clientMax = item.getClientMaxServerMsgId() == null ? 0L : item.getClientMaxServerMsgId();
                    
                    Long latest = latestServerMsgIdMap.get(item.getConversationId());

                    if (latest == null) {
                        latest = 0L; // 该会话无消息
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
        
        // 收集所有会话ID
        List<String> conversationIds = items.stream()
                .filter(item -> item != null && item.getConversationId() != null)
                .map(HeartbeatItem::getConversationId)
                .distinct()
                .collect(Collectors.toList());
        
        if (conversationIds.isEmpty()) {
            return result;
        }
        
        // 批量查询数据库
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
            // 数据库查询失败，使用业务异常枚举
            throw new ApiException(BusinessErrorCodeEnum.CHAT_BATCH_QUERY_MSG_ID_FAILED.getErrorMsg());
        }
        
        return result;
    }


    /**
     * 处理会话状态和分配逻辑
     * 
     * @param conversationId 会话ID
     * @param serverMsgId 消息ID
     * @param senderId 发送人id
     */
    private void handleConversation(String conversationId, Long serverMsgId, String senderId) {
        log.debug("🔍 开始处理客服会话: conversationId={}, serverMsgId={}, senderId={}", conversationId, serverMsgId, senderId);
        try {
            // 1. 检查会话是否已存在
            log.debug("📋 检查会话是否已存在: conversationId={}", conversationId);
            QueryWrapper<ChatConversationDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", conversationId);
            ChatConversationDO existingConversation = conversationMapper.selectOne(queryWrapper);
            
            if (existingConversation != null) {
                log.debug("✅ 会话已存在: conversationId={}, status={}", conversationId, existingConversation.getStatus());
                // 会话已存在 - 检查是否需要激活状态并推送给客户和其他客服
                ConversationStatusEnum currentStatus = ConversationStatusEnum.fromCode(existingConversation.getStatus());
                boolean needActivation = currentStatus != null && !currentStatus.isActive();
                log.debug("🔄 会话激活检查: conversationId={}, needActivation={}", conversationId, needActivation);
                activateExistingConversation(conversationId, serverMsgId, needActivation, senderId);
            } else {
                log.error("❌ 客服主动发起对客户的新对话,这不正常: conversationId={}, status=active", conversationId);
            }
            log.debug("✅ 客服会话处理完成: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("❌ 处理会话状态失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 处理已存在的会话（客服发送消息时）
     * @param conversationId 会话ID
     * @param serverMsgId 服务端消息ID
     * @param needActivation 是否需要激活会话状态（从waiting/closed等状态激活为active）
     */
    private void activateExistingConversation(String conversationId, Long serverMsgId, boolean needActivation,String senderId) {
    
        log.info("处理已存在会话: conversationId={}, needActivation={}", conversationId, needActivation);
        // 如果需要激活，更新会话状态为active
        if (needActivation) {
            try {
                UpdateWrapper<ChatConversationDO> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("conversation_id", conversationId)
                           .set("status", ConversationStatusEnum.ACTIVE.getCode())
                           .set("updated_at", new Date());
                
                int updateResult = conversationMapper.update(null, updateWrapper);
                if (updateResult > 0) {
                    log.error("按理来说,关闭状态的会话不存在客服主动发起的会话,会话状态已激活: conversationId={}, status=active", conversationId);
                } else {
                    log.warn("会话状态激活失败: conversationId={}", conversationId);
                }
            } catch (Exception e) {
                log.error("激活会话状态失败: conversationId={}", conversationId, e);
            }
        }
        // 查询群聊成员
        Set<String> memberIds = groupMemberCacheManager.getGroupMemberIds(conversationId);
        // 已存在会话：使用统一的消息分发接口
        notificationDispatcher.dispatch(conversationId, serverMsgId, senderId,memberIds);
    }

    /**
     * 自动更新发送者的已读记录到最新位置（发送消息时调用）
     * 
     * @param conversationId 会话ID
     * @param agentId 用户ID
     * @param serverMsgId 服务端消息ID
     */
    private void updateHasReadToLatest(String conversationId, String agentId, Long serverMsgId) {
        log.debug("自动更新发送者已读记录: conversationId={}, agentId={}, serverMsgId={}", 
                conversationId, agentId, serverMsgId);
        
        try {
            // 查询当前已读指针（userId是String，转换为Long）
            Long agentIdLong = Long.parseLong(agentId);
            UserConversationReadDO existingRecord = userConversationReadMapper.selectOne(
                new QueryWrapper<UserConversationReadDO>()
                    .eq("conversation_id", conversationId)
                    .eq("agent_id", agentIdLong)
            );
            
            if (existingRecord != null) {
                // 更新已存在的记录，使用乐观锁防止回退
                if (serverMsgId > existingRecord.getLastReadServerMsgId()) {
                    UpdateWrapper<UserConversationReadDO> updateWrapper = new UpdateWrapper<>();
                    updateWrapper.eq("conversation_id", conversationId)
                            .eq("agent_id", agentIdLong)
                            .eq("last_read_server_msg_id", existingRecord.getLastReadServerMsgId()) // 乐观锁条件
                            .set("last_read_server_msg_id", serverMsgId)
                            .set("updated_at", new Date());
                    
                    int updateResult = userConversationReadMapper.update(null, updateWrapper);
                    if (updateResult > 0) {
                        log.debug("发送者已读指针更新成功: conversationId={}, agentId={}, serverMsgId={}", 
                                conversationId, agentId, serverMsgId);
                    } else {
                        log.warn("发送者已读指针更新失败，可能被其他设备更新: conversationId={}, userId={}", 
                                conversationId, agentId);
                    }
                } else {
                    log.debug("发送者已读指针无需更新，当前值已大于等于新值: conversationId={}, userId={}, current={}, new={}", 
                            conversationId, agentId, existingRecord.getLastReadServerMsgId(), serverMsgId);
                }
            } else {
                // 插入新记录
                UserConversationReadDO newRecord = new UserConversationReadDO();
                newRecord.setConversationId(conversationId);
                newRecord.setAgentId(agentIdLong);
                newRecord.setLastReadServerMsgId(serverMsgId);
                newRecord.setUpdatedAt(new Date());
                
                int insertResult = userConversationReadMapper.insert(newRecord);
                if (insertResult > 0) {
                    log.debug("发送者已读指针插入成功: conversationId={}, userId={}, serverMsgId={}", 
                            conversationId, agentId, serverMsgId);
                } else {
                    log.error("发送者已读指针插入失败: conversationId={}, agentId={}", conversationId, agentId);
                    throw new RuntimeException("发送者已读指针插入失败");
                }
            }
            
        } catch (Exception e) {
            log.error("自动更新发送者已读记录失败: conversationId={}, userId={}, serverMsgId={}", 
                    conversationId, agentId, serverMsgId, e);
            throw new RuntimeException("自动更新发送者已读记录失败", e);
        }
    }

}
