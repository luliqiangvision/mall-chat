package com.treasurehunt.chat.httpservice;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.MallShopDO;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.utils.Conver;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.component.manager.ChatWindowManager;
import com.treasurehunt.chat.service.MallShopService;
import com.treasurehunt.chat.vo.ActiveConversations;
import com.treasurehunt.chat.enums.ConversationStatusEnum;
import com.treasurehunt.chat.mapper.ChatAgentMapper;
import com.treasurehunt.chat.vo.JoinConversationRequest;
import com.treasurehunt.chat.vo.JoinConversationResult;
import com.treasurehunt.chat.vo.PullMessageWithPagedQueryRequest;
import com.treasurehunt.chat.vo.ChatmessageWithPaged;
import com.treasurehunt.chat.vo.ChatMessage;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import com.treasurehunt.chat.mapper.UserConversationReadMapper;
import com.treasurehunt.chat.vo.ConversationInfo;
import com.treasurehunt.chat.vo.CheckMissingMessagesRequest;
import com.treasurehunt.chat.vo.ConversationViewVO;
import com.treasurehunt.chat.vo.MallShopVO;
import com.treasurehunt.chat.vo.CheckMissingMessagesResponse;
import com.treasurehunt.chat.vo.MarkAsReadRequest;
import com.treasurehunt.chat.mapper.ChatConversationMemberMapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 客服HTTP服务层
 * 负责处理客服登录时需要同步查询的业务逻辑
 */
@Slf4j
@Service
public class AgentHttpService {

    @Autowired
    private ChatConversationMapper conversationMapper;

    @Autowired
    private ChatMessageMapper chatMessageMapper;

    @Autowired
    private ChatWindowManager chatWindowManager;

    @Autowired
    private ConversationInfoService conversationInfoService;

    @Autowired
    private ChatConversationMemberMapper conversationMemberMapper;

    @Autowired
    private ChatAgentMapper chatAgentMapper;
    
    @Autowired
    private UserConversationReadMapper userConversationReadMapper;
    
    @Autowired
    private MallShopService mallShopService;

    /**
     * 店铺主接待会话列表：{@code agent_id = 当前客服} 且 {@code shop_id IS NOT NULL}。
     * 无店铺的公司级主接待见 {@link #listCorporateConversations(String, String)}。
     * 被拉进群协作的会话见 {@link #getParticipantConversations(String, String)}。
     */
    public ActiveConversations getConversations(String agentId, String businessLine) {
        log.debug("开始获取客服店铺主接待会话列表: agentId={}, businessLine={}", agentId, businessLine);
        
        try {
            List<ChatConversationDO> conversations = conversationMapper.selectList(
                new QueryWrapper<ChatConversationDO>()
                    .eq("business_line", businessLine)
                    .eq("agent_id", agentId)
                    .isNotNull("shop_id")
                    .in("status", "active", "waiting")
                    .orderByDesc("updated_at")
            );
            
            if (conversations.isEmpty()) {
                log.debug("客服没有活跃会话: agentId={}", agentId);
                return ActiveConversations.builder()
                        .conversations(new ArrayList<>())
                        .totalUnreadCount(0)
                        .conversationUnreadCounts(new HashMap<>())
                        .build();
            }
            
            // 2. 批量获取会话信息（包含最后一条消息和未读消息数）
            List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(conversations, agentId, true);
            
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
            
            return ActiveConversations.builder()
                    .conversations(conversationInfos)
                    .totalUnreadCount(totalUnreadCount)
                    .conversationUnreadCounts(conversationUnreadCounts)
                    .build();
                    
        } catch (Exception e) {
            log.error("客服获取活跃会话列表失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return ActiveConversations.builder()
                    .conversations(new ArrayList<>())
                    .totalUnreadCount(0)
                    .conversationUnreadCounts(new HashMap<>())
                    .build();
        }
    }

    /**
     * 参与协作会话列表：查 {@code chat_conversation_member}（agent、未退群），
     * 且排除当前客服已是主接待（{@code chat_conversation.agent_id}）的会话，避免与主接待列表重复。
     * 加入群聊时只写成员表，不写 {@code agent_id}。
     */
    public ActiveConversations getParticipantConversations(String agentId, String businessLine) {
        log.debug("开始获取客服参与协作会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            List<ChatConversationDO> conversations = conversationMemberMapper
                    .selectParticipantConversationsForAgent(businessLine, agentId);

            if (conversations.isEmpty()) {
                return ActiveConversations.builder()
                        .conversations(new ArrayList<>())
                        .totalUnreadCount(0)
                        .conversationUnreadCounts(new HashMap<>())
                        .build();
            }

            List<ConversationInfo> conversationInfos = conversationInfoService
                    .getConversationInfos(conversations, agentId, true);

            Integer totalUnreadCount = conversationInfos.stream()
                    .mapToInt(ConversationInfo::getUnreadCount)
                    .sum();

            Map<String, Integer> conversationUnreadCounts = conversationInfos.stream()
                    .collect(Collectors.toMap(
                            ConversationInfo::getConversationId,
                            ConversationInfo::getUnreadCount,
                            (existing, replacement) -> existing
                    ));

            return ActiveConversations.builder()
                    .conversations(conversationInfos)
                    .totalUnreadCount(totalUnreadCount)
                    .conversationUnreadCounts(conversationUnreadCounts)
                    .build();

        } catch (Exception e) {
            log.error("客服获取参与协作会话列表失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return ActiveConversations.builder()
                    .conversations(new ArrayList<>())
                    .totalUnreadCount(0)
                    .conversationUnreadCounts(new HashMap<>())
                    .build();
        }
    }

    /**
     * 没有配置客服接待的会话：同业务线下 {@code agent_id IS NULL} 且 {@code status=waiting}（如店铺售前绑定池空，进线未能分配主接待）。
     * 售前仅能看到自己在 {@code chat_agent_shop_relation} 绑定店铺下的此类会话。
     */
    public ActiveConversations listConversationsWithoutConfiguredAgentReception(String agentId, String businessLine) {
        log.debug("开始获取未配置客服接待的会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            List<Long> boundShopIds = chatAgentMapper.selectActiveShopIdsByBusinessLineAndAgent(businessLine, agentId);
            if (boundShopIds == null || boundShopIds.isEmpty()) {
                log.debug("客服无店铺绑定，未配置客服接待会话列表为空: agentId={}, businessLine={}", agentId, businessLine);
                return emptyActiveConversations();
            }

            List<ChatConversationDO> withoutReceptionConversations = conversationMapper.selectList(
                    new QueryWrapper<ChatConversationDO>()
                            .eq("business_line", businessLine)
                            .isNull("agent_id")
                            .eq("status", ConversationStatusEnum.WAITING.getCode())
                            .in("shop_id", boundShopIds)
                            .orderByAsc("created_at"));

            if (withoutReceptionConversations.isEmpty()) {
                log.debug("没有未配置客服接待的会话: agentId={}, businessLine={}", agentId, businessLine);
                return emptyActiveConversations();
            }

            List<ConversationInfo> conversationInfos = conversationInfoService
                    .getConversationInfos(withoutReceptionConversations, agentId, true);

            Integer totalUnreadCount = conversationInfos.stream()
                    .mapToInt(ConversationInfo::getUnreadCount)
                    .sum();

            Map<String, Integer> conversationUnreadCounts = conversationInfos.stream()
                    .collect(Collectors.toMap(
                            ConversationInfo::getConversationId,
                            ConversationInfo::getUnreadCount,
                            (existing, replacement) -> existing));

            return ActiveConversations.builder()
                    .conversations(conversationInfos)
                    .totalUnreadCount(totalUnreadCount)
                    .conversationUnreadCounts(conversationUnreadCounts)
                    .build();

        } catch (Exception e) {
            log.error("获取未配置客服接待的会话列表失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return emptyActiveConversations();
        }
    }

    /**
     * 公司级会话列表（无 {@code shop_id}）：同业务线下全部公司级 active/waiting 会话（不按主接待 agent_id 过滤），
     * 未分配主接待的 waiting 排在前面。{@code agentId} 仅用于未读数等展示，不参与列表筛选。
     * 供老板 / 公司级坐席工作台使用；店铺售前请用店铺主接待与「未配置客服接待」接口。
     */
    public ActiveConversations listCorporateConversations(String agentId, String businessLine) {
        log.debug("开始获取公司级会话列表: agentId={}, businessLine={}", agentId, businessLine);

        try {
            List<ChatConversationDO> unassignedCorporate = conversationMapper.selectList(
                    new QueryWrapper<ChatConversationDO>()
                            .eq("business_line", businessLine)
                            .isNull("shop_id")
                            .isNull("agent_id")
                            .eq("status", ConversationStatusEnum.WAITING.getCode())
                            .orderByAsc("created_at"));

            List<ChatConversationDO> activeCorporate = conversationMapper.selectList(
                    new QueryWrapper<ChatConversationDO>()
                            .eq("business_line", businessLine)
                            .isNull("shop_id")
                            .in("status", "active", "waiting")
                            .orderByDesc("updated_at"));

            LinkedHashMap<String, ChatConversationDO> merged = new LinkedHashMap<>();
            for (ChatConversationDO conversation : unassignedCorporate) {
                merged.put(conversation.getConversationId(), conversation);
            }
            for (ChatConversationDO conversation : activeCorporate) {
                merged.putIfAbsent(conversation.getConversationId(), conversation);
            }

            if (merged.isEmpty()) {
                log.debug("没有公司级会话: agentId={}, businessLine={}", agentId, businessLine);
                return emptyActiveConversations();
            }

            List<ChatConversationDO> conversations = new ArrayList<>(merged.values());
            List<ConversationInfo> conversationInfos = conversationInfoService
                    .getConversationInfos(conversations, agentId, true);

            Integer totalUnreadCount = conversationInfos.stream()
                    .mapToInt(ConversationInfo::getUnreadCount)
                    .sum();

            Map<String, Integer> conversationUnreadCounts = conversationInfos.stream()
                    .collect(Collectors.toMap(
                            ConversationInfo::getConversationId,
                            ConversationInfo::getUnreadCount,
                            (existing, replacement) -> existing));

            return ActiveConversations.builder()
                    .conversations(conversationInfos)
                    .totalUnreadCount(totalUnreadCount)
                    .conversationUnreadCounts(conversationUnreadCounts)
                    .build();

        } catch (Exception e) {
            log.error("获取公司级会话列表失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return emptyActiveConversations();
        }
    }

    private static ActiveConversations emptyActiveConversations() {
        return ActiveConversations.builder()
                .conversations(new ArrayList<>())
                .totalUnreadCount(0)
                .conversationUnreadCounts(new HashMap<>())
                .build();
    }

    /**
     * 客服加入会话
     * 
     * @param request 加入会话请求
     * @param agentId 客服ID
     * @return 加入结果
     */
    public JoinConversationResult grapConversation(JoinConversationRequest request, String agentId, String businessLine) {
        log.info("客服加入会话: agentId={}, businessLine={}, conversationId={}", agentId, businessLine, request.getConversationId());
        try {
            String conversationId = request.getConversationId();
            if (conversationId == null || conversationId.isEmpty()) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("会话ID不能为空")
                    .build();
            }
            
            QueryWrapper<ChatConversationDO> conversationQuery = new QueryWrapper<>();
            conversationQuery.eq("business_line", businessLine).eq("conversation_id", conversationId);
            ChatConversationDO conversation = conversationMapper.selectOne(conversationQuery);
            if (conversation == null) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("会话不存在")
                    .build();
            }
            
            QueryWrapper<ChatConversationMemberDO> memberQuery = new QueryWrapper<>();
            memberQuery.eq("conversation_id", conversationId)
                      .eq("business_line", businessLine)
                      .eq("member_type", "agent")
                      .eq("member_id", agentId)
                      .isNull("left_at");
            ChatConversationMemberDO existingMember = conversationMemberMapper.selectOne(memberQuery);
            if (existingMember != null) {
                log.info("客服已经是会话成员: agentId={}, conversationId={}", agentId, conversationId);
                List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(Arrays.asList(conversation), agentId, true);
                ConversationInfo conversationInfo = conversationInfos.get(0);
                return JoinConversationResult.builder()
                    .success(true)
                    .conversationInfo(conversationInfo)
                    .build();
            }

            boolean withoutConfiguredAgentReception = conversation.getAgentId() == null
                    || conversation.getAgentId().trim().isEmpty();
            if (withoutConfiguredAgentReception) {
                if (!ConversationStatusEnum.WAITING.getCode().equals(conversation.getStatus())) {
                    return JoinConversationResult.builder()
                            .success(false)
                            .errorMessage("仅未配置客服接待（waiting 且无主接待）的会话可认领")
                            .build();
                }
                if (conversation.getShopId() != null
                        && !isAgentBoundToShop(businessLine, agentId, conversation.getShopId())) {
                    return JoinConversationResult.builder()
                            .success(false)
                            .errorMessage("您未绑定该店铺，无法接待此会话")
                            .build();
                }
                return claimConversationWithoutConfiguredAgentReception(conversation, agentId, businessLine);
            }

            ChatConversationMemberDO member = new ChatConversationMemberDO();
            member.setConversationId(conversationId);
            member.setBusinessLine(businessLine);
            member.setMemberType("agent");
            member.setMemberId(agentId);
            member.setJoinedAt(new Date());
            member.setLeftAt(null);
            int inserted = conversationMemberMapper.insert(member);
            if (inserted <= 0) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("加入会话失败")
                    .build();
            }
            
            List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(Arrays.asList(conversation), agentId, true);
            ConversationInfo conversationInfo = conversationInfos.get(0);
            log.info("客服协作加入会话: agentId={}, conversationId={}", agentId, conversationId);
            return JoinConversationResult.builder()
                .success(true)
                .conversationInfo(conversationInfo)
                .build();
                
        } catch (Exception e) {
            log.error("客服加入会话失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return JoinConversationResult.builder()
                .success(false)
                .errorMessage("加入会话失败: " + e.getMessage())
                .build();
        }
    }

    private boolean isAgentBoundToShop(String businessLine, String agentId, Long shopId) {
        List<Long> shopIds = chatAgentMapper.selectActiveShopIdsByBusinessLineAndAgent(businessLine, agentId);
        return shopIds != null && shopIds.contains(shopId);
    }

    /**
     * 接待「未配置客服」的会话：写入主接待 {@code agent_id}、状态 active，并加入群成员。
     */
    private JoinConversationResult claimConversationWithoutConfiguredAgentReception(ChatConversationDO conversation,
                                                                                    String agentId,
                                                                                    String businessLine) {
        String conversationId = conversation.getConversationId();
        UpdateWrapper<ChatConversationDO> claimUpdate = new UpdateWrapper<>();
        claimUpdate.eq("conversation_id", conversationId)
                .eq("business_line", businessLine)
                .isNull("agent_id")
                .eq("status", ConversationStatusEnum.WAITING.getCode())
                .set("agent_id", agentId)
                .set("status", ConversationStatusEnum.ACTIVE.getCode())
                .set("updated_at", new Date());
        int claimed = conversationMapper.update(null, claimUpdate);
        if (claimed <= 0) {
            return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("会话已被其他客服认领或状态已变更")
                    .build();
        }

        chatWindowManager.bindAgents(conversationId, Collections.singletonList(agentId));

        ChatConversationDO updated = conversationMapper.selectOne(
                new QueryWrapper<ChatConversationDO>()
                        .eq("business_line", businessLine)
                        .eq("conversation_id", conversationId));
        List<ConversationInfo> conversationInfos = conversationInfoService
                .getConversationInfos(Collections.singletonList(updated), agentId, true);
        log.info("客服接待未配置客服的会话成功: agentId={}, conversationId={}", agentId, conversationId);
        return JoinConversationResult.builder()
                .success(true)
                .conversationInfo(conversationInfos.get(0))
                .build();
    }

    /**
     * 被人拉进群（邀请加入会话）
     * @param conversationId 会话ID
     * @param agentId 被邀请的客服ID
     * @param inviterAgentId 邀请人客服ID
     * @return 加入结果
     */
    public JoinConversationResult inviteToConversation(String conversationId, String agentId, String inviterAgentId, String businessLine) {
        log.info("邀请客服加入会话: conversationId={}, agentId={}, inviterAgentId={}", 
                conversationId, agentId, inviterAgentId);
        try {
            if (conversationId == null || conversationId.isEmpty()) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("会话ID不能为空")
                    .build();
            }
            
            // 2. 验证会话是否存在
            QueryWrapper<ChatConversationDO> conversationQuery = new QueryWrapper<>();
            conversationQuery.eq("business_line", businessLine).eq("conversation_id", conversationId);
            ChatConversationDO conversation = conversationMapper.selectOne(conversationQuery);
            if (conversation == null) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("会话不存在")
                    .build();
            }
            
            // 3. 检查客服是否已经在群里（是否已经是会话成员）
            QueryWrapper<ChatConversationMemberDO> memberQuery = new QueryWrapper<>();
            memberQuery.eq("conversation_id", conversationId)
                      .eq("business_line", businessLine)
                      .eq("member_type", "agent")
                      .eq("member_id", agentId)
                      .isNull("left_at");
            ChatConversationMemberDO existingMember = conversationMemberMapper.selectOne(memberQuery);
            if (existingMember != null) {
                log.info("客服已经是会话成员: agentId={}, conversationId={}", agentId, conversationId);
                // 返回现有会话信息
                List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(Arrays.asList(conversation), agentId, true);
                ConversationInfo conversationInfo = conversationInfos.get(0);
                return JoinConversationResult.builder()
                    .success(true)
                    .conversationInfo(conversationInfo)
                    .build();
            }
            
            // 4. 仅写入群成员表，不修改 chat_conversation.agent_id（主接待唯一）
            ChatConversationMemberDO member = new ChatConversationMemberDO();
            member.setConversationId(conversationId);
            member.setBusinessLine(businessLine);
            member.setMemberType("agent");
            member.setMemberId(agentId);
            member.setJoinedAt(new Date());
            member.setLeftAt(null);
            int inserted = conversationMemberMapper.insert(member);
            if (inserted <= 0) {
                return JoinConversationResult.builder()
                    .success(false)
                    .errorMessage("加入会话失败")
                    .build();
            }
            
            // 5. 返回会话信息
            List<ConversationInfo> conversationInfos = conversationInfoService.getConversationInfos(Arrays.asList(conversation), agentId, true);
            ConversationInfo conversationInfo = conversationInfos.get(0);
            log.info("客服成功加入会话: agentId={}, conversationId={}", agentId, conversationId);
            return JoinConversationResult.builder()
                .success(true)
                .conversationInfo(conversationInfo)
                .build();
                
        } catch (Exception e) {
            log.error("客服加入会话失败: agentId={}, businessLine={}", agentId, businessLine, e);
            return JoinConversationResult.builder()
                .success(false)
                .errorMessage("加入会话失败: " + e.getMessage())
                .build();
        }
    }

    /**
     * 分页拉取消息（历史消息查询）
     *
     * @param request 分页拉取请求
     * @param agentId 客服ID
     * @return 分页消息结果
     */
    public ChatmessageWithPaged pullMessageWithPagedQuery(PullMessageWithPagedQueryRequest request, String agentId, String businessLine) {
        log.info("客服分页拉取消息: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
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
            log.error("客服分页拉取消息失败: agentId={}, businessLine={}", agentId, businessLine, e);
            throw new RuntimeException("客服分页拉取消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除会话（客服删除/归档）
     * 
     * @param conversationId 会话ID
     * @param agentId 客服ID
     * @return 删除结果
     */
    public String deleteConversation(String conversationId, String agentId, String businessLine) {
        log.info("客服删除会话: agentId={}, businessLine={}, conversationId={}", agentId, businessLine, conversationId);
        try {
            boolean success = chatWindowManager.deleteConversationByAgent(conversationId, agentId);
            if (success) {
                return "会话删除成功";
            } else {
                throw new RuntimeException("会话删除失败，可能无权限或已删除");
            }
        } catch (Exception e) {
            log.error("客服删除会话失败: agentId={}, businessLine={}, conversationId={}", agentId, businessLine, conversationId, e);
            throw new RuntimeException("客服删除会话失败: " + e.getMessage(), e);
        }
    }

    /**
     * 登录初始化聊天窗口：店铺主接待 + 参与协作的并集（不含无 shopId 公司级会话，见 {@link #listCorporateConversations}）。
     */
    public Map<String, ConversationViewVO> getChatWindowList(String agentId, String businessLine) {
        log.debug("开始获取客服聊天窗口列表: agentId={}, businessLine={}", agentId, businessLine);
        
        try {
            List<ChatConversationDO> primaryHostConversations = conversationMapper.selectList(
                new QueryWrapper<ChatConversationDO>()
                    .eq("business_line", businessLine)
                    .eq("agent_id", agentId)
                    .isNotNull("shop_id")
                    .in("status", "active", "waiting")
                    .orderByDesc("updated_at")
            );
            List<ChatConversationDO> participantConversations = conversationMemberMapper
                    .selectParticipantConversationsForAgent(businessLine, agentId);

            LinkedHashMap<String, ChatConversationDO> merged = new LinkedHashMap<>();
            for (ChatConversationDO conversation : primaryHostConversations) {
                merged.put(conversation.getConversationId(), conversation);
            }
            for (ChatConversationDO conversation : participantConversations) {
                merged.putIfAbsent(conversation.getConversationId(), conversation);
            }

            List<ChatConversationDO> conversations = new ArrayList<>(merged.values());
            if (conversations.isEmpty()) {
                log.debug("客服没有可展示的聊天窗口: agentId={}", agentId);
                return new HashMap<>();
            }
            
            Map<String, ConversationViewVO> result = new HashMap<>();
            
            // 2. 批量查询已读指针
            List<String> conversationIds = conversations.stream()
                    .map(ChatConversationDO::getConversationId)
                    .collect(Collectors.toList());
            
            // 查询客服已读指针（使用agent_id字段）
            List<UserConversationReadDO> readRecords = userConversationReadMapper.selectList(
                new QueryWrapper<UserConversationReadDO>()
                    .in("conversation_id", conversationIds)
                    .eq("agent_id", Long.parseLong(agentId))
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
                    MallShopDO shopDO = mallShopService.getShopByBusinessLineAndId(businessLine, conversation.getShopId());
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
            
            log.debug("客服聊天窗口列表获取完成: agentId={}, conversationCount={}", agentId, result.size());
            return result;
            
        } catch (Exception e) {
            log.error("获取客服聊天窗口列表失败: agentId={}, businessLine={}", agentId, businessLine, e);
            throw new RuntimeException("获取客服聊天窗口列表失败", e);
        }
    }

    /**
     * 检查缺失的消息
     * 
     * @param request 检查缺失消息请求
     * @param agentId 客服ID
     * @return 缺失消息响应
     */
    public CheckMissingMessagesResponse checkMissingMessages(CheckMissingMessagesRequest request, String agentId, String businessLine) {
        log.debug("开始检查缺失消息: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
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
            log.error("检查缺失消息失败: agentId={}, businessLine={}", agentId, businessLine, e);
            throw new RuntimeException("检查缺失消息失败: " + e.getMessage(), e);
        }
    }

    /**
     * 标记已读
     * 
     * @param request 标记已读请求
     * @param agentId 客服ID
     * @return 是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markAsRead(MarkAsReadRequest request, String agentId, String businessLine) {
        log.debug("开始标记已读: agentId={}, businessLine={}, request={}", agentId, businessLine, request);
        
        try {
            String conversationId = request.getConversationId();
            Long serverMsgId = request.getServerMsgId();
            
            // 查询当前已读指针（使用agent_id字段）
            UserConversationReadDO existingRecord = userConversationReadMapper.selectOne(
                new QueryWrapper<UserConversationReadDO>()
                    .eq("conversation_id", conversationId)
                    .eq("agent_id", Long.parseLong(agentId))
            );
            
            if (existingRecord != null) {
                // 更新已存在的记录，使用乐观锁防止回退
                if (serverMsgId > existingRecord.getLastReadServerMsgId()) {
                    UpdateWrapper<UserConversationReadDO> updateWrapper = new UpdateWrapper<>();
                    updateWrapper.eq("conversation_id", conversationId)
                            .eq("agent_id", Long.parseLong(agentId))
                            .eq("last_read_server_msg_id", existingRecord.getLastReadServerMsgId()) // 乐观锁条件
                            .set("last_read_server_msg_id", serverMsgId)
                            .set("updated_at", new Date());
                    
                    int updateResult = userConversationReadMapper.update(null, updateWrapper);
                    if (updateResult > 0) {
                        log.debug("已读指针更新成功: conversationId={}, agentId={}, serverMsgId={}", 
                                conversationId, agentId, serverMsgId);
                        return true;
                    } else {
                        log.warn("已读指针更新失败，可能被其他设备更新: conversationId={}, agentId={}", 
                                conversationId, agentId);
                        return false;
                    }
                } else {
                    log.debug("已读指针无需更新，当前值已大于等于新值: conversationId={}, agentId={}, current={}, new={}", 
                            conversationId, agentId, existingRecord.getLastReadServerMsgId(), serverMsgId);
                    return true;
                }
            } else {
                // 插入新记录（使用agent_id字段）
                UserConversationReadDO newRecord = new UserConversationReadDO();
                newRecord.setConversationId(conversationId);
                newRecord.setAgentId(Long.parseLong(agentId));
                newRecord.setLastReadServerMsgId(serverMsgId);
                newRecord.setUpdatedAt(new Date());
                
                int insertResult = userConversationReadMapper.insert(newRecord);
                if (insertResult > 0) {
                    log.debug("已读指针插入成功: conversationId={}, agentId={}, serverMsgId={}", 
                            conversationId, agentId, serverMsgId);
                    return true;
                } else {
                    log.error("已读指针插入失败: conversationId={}, agentId={}", conversationId, agentId);
                    return false;
                }
            }
            
        } catch (Exception e) {
            log.error("标记已读失败: agentId={}, businessLine={}, request={}", agentId, businessLine, request, e);
            throw new RuntimeException("标记已读失败", e);
        }
    }
}
