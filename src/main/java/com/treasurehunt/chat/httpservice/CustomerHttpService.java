package com.treasurehunt.chat.httpservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.MallShopDO;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.mapper.UserConversationReadMapper;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import com.treasurehunt.chat.service.MallShopService;
import com.treasurehunt.chat.vo.ConversationViewVO;
import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.vo.MallShopVO;
import com.treasurehunt.chat.utils.Conver;
import com.treasurehunt.chat.vo.CheckUnreadMessagesResponse;
import com.treasurehunt.chat.vo.CheckMissingMessagesRequest;
import com.treasurehunt.chat.vo.CheckMissingMessagesResponse;
import com.treasurehunt.chat.vo.CustomerPullMessageWithPagedQueryRequest;
import com.treasurehunt.chat.vo.ConversationInfo;
import com.treasurehunt.chat.vo.ChatmessageWithPaged;
import com.treasurehunt.chat.vo.MarkAsReadRequest;
import com.treasurehunt.chat.vo.ChatMessagesInitResult;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 客户HTTP服务层
 * 负责处理客户登录时需要同步查询的业务逻辑
 */
@Slf4j
@Service
public class CustomerHttpService {

    @Autowired
    private ChatConversationMapper chatConversationMapper;
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private UserConversationReadMapper userConversationReadMapper;
    
    @Autowired
    private ConversationInfoService conversationInfoService;
    
    @Autowired
    private MallShopService mallShopService;

    /**
     * 根据conversationId初始化聊天窗口
     * 规则：
     * - 根据 conversationId 和 userId 查询所有未读消息（server_msg_id > last_read_server_msg_id）
     * - 若有未读，再追加未读最小 server_msg_id 之前的 5 条消息
     * - 若没有未读，则返回最近 10 条消息
     * 返回按 server_msg_id 升序排列
     */
    public ChatMessagesInitResult initChatWindowByConversationId(String conversationId, Long userId) {
        try {
            // 获取已读指针
            Long lastRead = 0L;
            UserConversationReadDO readDO = userConversationReadMapper.selectOne(
                new QueryWrapper<UserConversationReadDO>()
                    .eq("conversation_id", conversationId)
                    .eq("user_id", userId)
            );
            if (readDO != null && readDO.getLastReadServerMsgId() != null) {
                lastRead = readDO.getLastReadServerMsgId();
            }

            // 查询未读消息（升序）
            List<ChatMessageDO> unread = chatMessageMapper.selectList(
                new QueryWrapper<ChatMessageDO>()
                    .eq("conversation_id", conversationId)
                    .gt("server_msg_id", lastRead)
                    .orderByAsc("server_msg_id")
            );

            List<ChatMessageDO> resultList;
            if (unread == null || unread.isEmpty()) {
                // 无未读，取最近10条（按server_msg_id倒序），再反转为升序返回
                List<ChatMessageDO> latestDesc = chatMessageMapper.selectList(
                    new QueryWrapper<ChatMessageDO>()
                        .eq("conversation_id", conversationId)
                        .orderByDesc("server_msg_id")
                        .last("limit 10")
                );
                java.util.Collections.reverse(latestDesc);
                resultList = latestDesc;
            } else {
                // 有未读，找最小未读id，补之前5条
                long minUnreadId = unread.get(0).getServerMsgId();
                List<ChatMessageDO> prevDesc = chatMessageMapper.selectList(
                    new QueryWrapper<ChatMessageDO>()
                        .eq("conversation_id", conversationId)
                        .lt("server_msg_id", minUnreadId)
                        .orderByDesc("server_msg_id")
                        .last("limit 5")
                );
                java.util.Collections.reverse(prevDesc);
                resultList = new java.util.ArrayList<>(prevDesc.size() + unread.size());
                resultList.addAll(prevDesc);
                resultList.addAll(unread);
            }

            List<ChatMessage> vos = resultList.stream()
                .map(Conver::toChatMessage)
                .collect(Collectors.toList());

            return ChatMessagesInitResult.builder()
                .conversationId(conversationId)
                .chatMessages(vos)
                .build();
        } catch (Exception e) {
            log.error("根据conversationId初始化聊天窗口失败: conversationId={}, userId={}", conversationId, userId, e);
            throw new RuntimeException("根据conversationId初始化聊天窗口失败: " + e.getMessage(), e);
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
            List<ChatConversationDO> conversations = chatConversationMapper.selectList(
                new QueryWrapper<ChatConversationDO>()
                    .eq("customer_id", userId)
                    .in("status", "active", "waiting")
                    .orderByDesc("updated_at")
            );
            
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
            List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(conversations, String.valueOf(userId),false);
            
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
     * 获取客户聊天窗口列表
     * 
     * @param userId 用户ID
     * @return 会话视图Map，key为会话ID，value为会话视图VO
     */
    public Map<String, ConversationViewVO> getChatWindowList(Long userId) {
        log.debug("开始获取客户聊天窗口列表: userId={}", userId);
        
        try {
            // 1. 查询用户参与的所有会话
            List<ChatConversationDO> conversations = chatConversationMapper.selectList(
                new QueryWrapper<ChatConversationDO>()
                    .eq("customer_id", userId)
                    .in("status", "active", "waiting")
                    .orderByDesc("updated_at")
            );
            
            if (conversations.isEmpty()) {
                log.debug("用户没有参与任何会话: userId={}", userId);
                return new HashMap<>();
            }
            
            Map<String, ConversationViewVO> result = new HashMap<>();
            
            // 2. 批量查询已读指针
            List<String> conversationIds = conversations.stream()
                    .map(ChatConversationDO::getConversationId)
                    .collect(Collectors.toList());
            
            List<UserConversationReadDO> readRecords = userConversationReadMapper.selectList(
                new QueryWrapper<UserConversationReadDO>()
                    .in("conversation_id", conversationIds)
                    .eq("user_id", userId)
            );
            
            Map<String, Long> readPointerMap = readRecords.stream()
                    .collect(Collectors.toMap(
                            UserConversationReadDO::getConversationId,
                            UserConversationReadDO::getLastReadServerMsgId
                    ));
            
            // 3. 为每个会话构建视图
            for (ChatConversationDO conversation : conversations) {
                String conversationId = conversation.getConversationId();
                Long lastReadServerMsgId = readPointerMap.getOrDefault(conversationId, 0L);
                
                // 查询该会话的所有消息
                List<ChatMessageDO> allMessages = chatMessageMapper.selectList(
                    new QueryWrapper<ChatMessageDO>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("server_msg_id")
                );
                
                ConversationViewVO viewVO;
                
                // 查询店铺信息
                MallShopVO shopVO = null;
                if (conversation.getShopId() != null) {
                    MallShopDO shopDO = mallShopService.getShopById(conversation.getShopId());
                    shopVO = Conver.toMallShopVO(shopDO);
                }
                
                if (allMessages.isEmpty()) {
                    // 没有任何消息，返回店铺信息，确保能看到对话框
                    viewVO = ConversationViewVO.builder()
                            .messages(new ArrayList<>())
                            .unreadCount(0)
                            .shop(shopVO)
                            .build();
                } else {
                    // 分离已读和未读消息
                    List<ChatMessageDO> readMessages = allMessages.stream()
                            .filter(msg -> msg.getServerMsgId() <= lastReadServerMsgId)
                            .collect(Collectors.toList());
                    
                    List<ChatMessageDO> unreadMessages = allMessages.stream()
                            .filter(msg -> msg.getServerMsgId() > lastReadServerMsgId)
                            .collect(Collectors.toList());
                    
                    List<ChatMessage> resultMessages = new ArrayList<>();
                    
                    if (!unreadMessages.isEmpty()) {
                        // 有未读消息：5条已读消息 + 所有未读消息
                        int readCount = Math.min(5, readMessages.size());
                        List<ChatMessageDO> recentReadMessages = readMessages.stream()
                                .skip(Math.max(0, readMessages.size() - readCount))
                                .collect(Collectors.toList());
                        
                        resultMessages.addAll(recentReadMessages.stream()
                                .map(Conver::toChatMessage)
                                .collect(Collectors.toList()));
                        
                        resultMessages.addAll(unreadMessages.stream()
                                .map(Conver::toChatMessage)
                                .collect(Collectors.toList()));
                    } else {
                        // 没有未读消息：10条已读消息
                        int readCount = Math.min(10, readMessages.size());
                        List<ChatMessageDO> recentReadMessages = readMessages.stream()
                                .skip(Math.max(0, readMessages.size() - readCount))
                                .collect(Collectors.toList());
                        
                        resultMessages.addAll(recentReadMessages.stream()
                                .map(Conver::toChatMessage)
                                .collect(Collectors.toList()));
                    }
                    
                    viewVO = ConversationViewVO.builder()
                            .messages(resultMessages)
                            .unreadCount(unreadMessages.size())
                            .shop(shopVO)
                            .build();
                }
                
                result.put(conversationId, viewVO);
            }
            
            log.debug("客户聊天窗口列表获取完成: userId={}, conversationCount={}", userId, result.size());
            return result;
            
        } catch (Exception e) {
            log.error("获取客户聊天窗口列表失败: userId={}", userId, e);
            throw new RuntimeException("获取客户聊天窗口列表失败", e);
        }
    }

    /**
     * 客户分页拉取历史消息
     * 
     * @param request 分页拉取请求
     * @param userId 用户ID
     * @return 分页消息结果
     */
    public ChatmessageWithPaged pullMessageWithPagedQuery(CustomerPullMessageWithPagedQueryRequest request, Long userId) {
        log.debug("开始客户分页拉取消息: userId={}, request={}", userId, request);
        
        try {
            // 分页查询
            QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", request.getConversationId())
            .orderByDesc("server_msg_id")
            .last("limit " + request.getPageSize() + " offset " + (request.getCurrentPage() - 1) * request.getPageSize());
            List<ChatMessageDO> chatMessages = chatMessageMapper.selectList(queryWrapper);
            List<ChatMessage> chatMessagesVO = chatMessages.stream().map(Conver::toChatMessage).collect(Collectors.toList());
            return ChatmessageWithPaged.builder()
            .conversationId(request.getConversationId())
            .currentPage(request.getCurrentPage())
            .chatMessages(chatMessagesVO)
            .build();
        } catch (Exception e) {
            log.error("客户分页拉取消息失败: userId={}", userId, e);
            throw new RuntimeException("客户分页拉取消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查缺失的消息
     * 
     * @param request 检查缺失消息请求
     * @param userId 用户ID
     * @return 缺失消息响应
     */
    public CheckMissingMessagesResponse checkMissingMessages(CheckMissingMessagesRequest request, Long userId) {
        log.debug("开始检查缺失消息: userId={}, request={}", userId, request);
        
        try {
            String conversationId = request.getConversationId();
            Long startServerMsgId = request.getStartServerMsgId();
            Long endServerMsgId = request.getEndServerMsgId();
            
            List<ChatMessage> missingMessages = new ArrayList<>();
            
            // 范围查询（性能最佳）
            if (startServerMsgId != null && endServerMsgId != null && endServerMsgId > startServerMsgId) {
                QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("conversation_id", conversationId)
                    .gt("server_msg_id", startServerMsgId)
                    .lt("server_msg_id", endServerMsgId)
                    .orderByAsc("server_msg_id");
                
                List<ChatMessageDO> foundMessages = chatMessageMapper.selectList(queryWrapper);
                missingMessages = foundMessages.stream()
                    .map(Conver::toChatMessage)
                    .collect(Collectors.toList());
            }
            
            boolean hasMissingMessages = !missingMessages.isEmpty();
            
            return CheckMissingMessagesResponse.builder()
                .hasMissingMessages(hasMissingMessages)
                .missingMessages(missingMessages)
                .missingCount(missingMessages.size())
                .conversationId(conversationId)
                .build();
                
        } catch (Exception e) {
            log.error("检查缺失消息失败: userId={}", userId, e);
            throw new RuntimeException("检查缺失消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 标记已读
     * 
     * @param request 标记已读请求
     * @param userId 用户ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(MarkAsReadRequest request, Long userId) {
        log.debug("开始标记已读: userId={}, request={}", userId, request);
        
        try {
            String conversationId = request.getConversationId();
            Long serverMsgId = request.getServerMsgId();
            
            // 查询当前已读指针
            UserConversationReadDO existingRecord = userConversationReadMapper.selectOne(
                new QueryWrapper<UserConversationReadDO>()
                    .eq("conversation_id", conversationId)
                    .eq("user_id", userId)
            );
            
            if (existingRecord != null) {
                // 更新已存在的记录，使用乐观锁防止回退
                if (serverMsgId > existingRecord.getLastReadServerMsgId()) {
                    UpdateWrapper<UserConversationReadDO> updateWrapper = new UpdateWrapper<>();
                    updateWrapper.eq("conversation_id", conversationId)
                            .eq("user_id", userId)
                            .eq("last_read_server_msg_id", existingRecord.getLastReadServerMsgId()) // 乐观锁条件
                            .set("last_read_server_msg_id", serverMsgId)
                            .set("updated_at", new Date());
                    
                    int updateResult = userConversationReadMapper.update(null, updateWrapper);
                    if (updateResult > 0) {
                        log.debug("已读指针更新成功: conversationId={}, userId={}, serverMsgId={}", 
                                conversationId, userId, serverMsgId);
                        return true;
                    } else {
                        log.warn("已读指针更新失败，可能被其他设备更新: conversationId={}, userId={}", 
                                conversationId, userId);
                        return false;
                    }
                } else {
                    log.debug("已读指针无需更新，当前值已大于等于新值: conversationId={}, userId={}, current={}, new={}", 
                            conversationId, userId, existingRecord.getLastReadServerMsgId(), serverMsgId);
                    return true;
                }
            } else {
                // 插入新记录
                UserConversationReadDO newRecord = new UserConversationReadDO();
                newRecord.setConversationId(conversationId);
                newRecord.setUserId(userId);
                newRecord.setLastReadServerMsgId(serverMsgId);
                newRecord.setUpdatedAt(new Date());
                
                int insertResult = userConversationReadMapper.insert(newRecord);
                if (insertResult > 0) {
                    log.debug("已读指针插入成功: conversationId={}, userId={}, serverMsgId={}", 
                            conversationId, userId, serverMsgId);
                    return true;
                } else {
                    log.error("已读指针插入失败: conversationId={}, userId={}", conversationId, userId);
                    return false;
                }
            }
            
        } catch (Exception e) {
            log.error("标记已读失败: userId={}, request={}", userId, request, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}
