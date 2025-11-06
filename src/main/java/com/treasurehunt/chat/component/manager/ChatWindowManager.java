package com.treasurehunt.chat.component.manager;

import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.enums.ConversationStatusEnum;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.component.cache.GroupMemberCacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聊天窗口管理器
 * 统一管理聊天窗口的创建、删除、成员变动等操作
 * 集成群聊成员缓存管理，确保人员变动被锁定在几个核心方法中
 */
@Slf4j
@Service
public class ChatWindowManager {
    
    @Autowired
    private ChatConversationMapper conversationMapper;
    
    @Autowired
    private GroupMemberCacheManager groupMemberCacheManager;
    
    /**
     * 绑定多个客服到聊天窗口（群聊模式）
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param agentIds 多个客服ID列表
     */
    @Transactional
    public void bindAgents(String conversationId, List<String> agentIds) {
        try {
            log.info("绑定多个客服到聊天窗口: {} -> 客服数量: {}", conversationId, agentIds.size());
            
            // 创建群聊成员记录并批量添加（缓存管理类会自动处理数据库和缓存）
            List<ChatConversationMemberDO> members = agentIds.stream()
                    .map(agentId -> {
                        ChatConversationMemberDO member = new ChatConversationMemberDO();
                        member.setConversationId(conversationId);
                        member.setMemberType("agent");
                        member.setMemberId(agentId);
                        member.setJoinedAt(new Date());
                        return member;
                    })
                    .collect(Collectors.toList());
            
            // 批量添加到群聊（缓存管理类自动处理数据库和缓存更新）
            groupMemberCacheManager.addGroupMembers(conversationId, members);
            
            log.info("成功绑定多个客服到聊天窗口: {} -> 客服数量: {}", conversationId, agentIds.size());
            
        } catch (Exception e) {
            log.error("绑定多个客服到聊天窗口失败: {} -> 客服数量: {}", conversationId, agentIds.size(), e);
            throw new RuntimeException("绑定客服失败", e);
        }
    }
    
    /**
     * 单个客服从聊天窗口退出（群聊模式）
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param agentId 要退出的客服ID
     */
    @Transactional
    public void unbindAgent(String conversationId, String agentId) {
        try {
            log.info("客服从聊天窗口退出: {} -> {}", conversationId, agentId);
            
            // 检查客服是否在群中
            if (!groupMemberCacheManager.isGroupMember(conversationId, agentId)) {
                log.warn("客服不在聊天窗口中: {} -> {}", conversationId, agentId);
                return;
            }
            
            // 从群聊移除（缓存管理类自动处理数据库和缓存更新）
            groupMemberCacheManager.removeGroupMember(conversationId, agentId);
            
            log.info("成功移除客服: {} -> {}", conversationId, agentId);
            
        } catch (Exception e) {
            log.error("移除客服失败: {} -> {}", conversationId, agentId, e);
            throw new RuntimeException("移除客服失败", e);
        }
    }
    
    /**
     * 多个客服从聊天窗口退出（批量解绑）
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param agentIds 要退出的多个客服ID列表
     */
    @Transactional
    public void unbindAgents(String conversationId, List<String> agentIds) {
        try {
            log.info("批量移除客服: {} -> 客服数量: {}", conversationId, agentIds.size());
            
            // 过滤出实际存在的客服ID
            List<String> existingAgentIds = agentIds.stream()
                    .filter(agentId -> groupMemberCacheManager.isGroupMember(conversationId, agentId))
                    .collect(Collectors.toList());
            
            if (existingAgentIds.isEmpty()) {
                log.warn("没有客服在聊天窗口中: {}", conversationId);
                return;
            }
            
            // 批量移除（缓存管理类自动处理数据库和缓存更新）
            groupMemberCacheManager.removeGroupMembers(conversationId, existingAgentIds);
            
            log.info("成功批量移除客服: {} -> 实际移除数量: {}", conversationId, existingAgentIds.size());
            
        } catch (Exception e) {
            log.error("批量移除客服失败: {} -> 客服数量: {}", conversationId, agentIds.size(), e);
            throw new RuntimeException("批量移除客服失败", e);
        }
    }
    
    /**
     * 添加客户到聊天窗口
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param customerId 客户ID
     */
    @Transactional
    public void addCustomer(String conversationId, String customerId) {
        try {
            log.info("添加客户到聊天窗口: {} -> {}", conversationId, customerId);
            
            // 检查客户是否已在群中
            if (groupMemberCacheManager.isGroupMember(conversationId, customerId)) {
                log.warn("客户已在聊天窗口中: {} -> {}", conversationId, customerId);
                return;
            }
            
            // 创建群聊成员记录
            ChatConversationMemberDO member = new ChatConversationMemberDO();
            member.setConversationId(conversationId);
            member.setMemberType("customer");
            member.setMemberId(customerId);
            member.setJoinedAt(new Date());
            
            // 添加到群聊（自动更新缓存）
            groupMemberCacheManager.addGroupMember(conversationId, member);
            
            log.info("成功添加客户到聊天窗口: {} -> {}", conversationId, customerId);
            
        } catch (Exception e) {
            log.error("添加客户到聊天窗口失败: {} -> {}", conversationId, customerId, e);
            throw new RuntimeException("添加客户失败", e);
        }
    }
    
    /**
     * 移除客户从聊天窗口
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param customerId 客户ID
     */
    @Transactional
    public void removeCustomer(String conversationId, String customerId) {
        try {
            log.info("移除客户从聊天窗口: {} -> {}", conversationId, customerId);
            
            // 检查客户是否在群中
            if (!groupMemberCacheManager.isGroupMember(conversationId, customerId)) {
                log.warn("客户不在聊天窗口中: {} -> {}", conversationId, customerId);
                return;
            }
            
            // 从群聊移除（自动更新缓存）
            groupMemberCacheManager.removeGroupMember(conversationId, customerId);
            
            log.info("成功移除客户: {} -> {}", conversationId, customerId);
            
        } catch (Exception e) {
            log.error("移除客户失败: {} -> {}", conversationId, customerId, e);
            throw new RuntimeException("移除客户失败", e);
        }
    }
    
    /**
     * 系统自动分配客服到聊天窗口
     * 自动更新群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param agentId 客服ID
     */
    @Transactional
    public void assignAgentBySystem(String conversationId, String agentId) {
        try {
            log.info("系统分配客服到聊天窗口: {} -> {}", conversationId, agentId);
            
            // 检查客服是否已在群中
            if (groupMemberCacheManager.isGroupMember(conversationId, agentId)) {
                log.warn("客服已在聊天窗口中: {} -> {}", conversationId, agentId);
                return;
            }
            
            // 创建群聊成员记录
            ChatConversationMemberDO member = new ChatConversationMemberDO();
            member.setConversationId(conversationId);
            member.setMemberType("agent");
            member.setMemberId(agentId);
            member.setJoinedAt(new Date());
            
            // 添加到群聊（自动更新缓存）
            groupMemberCacheManager.addGroupMember(conversationId, member);
            
            log.info("成功分配客服到聊天窗口: {} -> {}", conversationId, agentId);
            
        } catch (Exception e) {
            log.error("系统分配客服失败: {} -> {}", conversationId, agentId, e);
            throw new RuntimeException("系统分配客服失败", e);
        }
    }
    
    /**
     * 软删除聊天窗口（客户主动删除）
     * 自动清理群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param customerId 客户ID
     * @return 是否成功删除
     */
    @Transactional
    public boolean deleteConversationByCustomer(String conversationId, String customerId) {
        try {
            log.info("客户删除聊天窗口: {} -> {}", conversationId, customerId);
            
            // 1. 检查客户是否在群中
            if (!groupMemberCacheManager.isGroupMember(conversationId, customerId)) {
                log.warn("客户不在聊天窗口中，无法删除: {} -> {}", conversationId, customerId);
                return false;
            }
            
            // 2. 更新聊天窗口状态为已删除
            ChatConversationDO conversation = new ChatConversationDO();
            conversation.setConversationId(conversationId);
            conversation.setStatus(ConversationStatusEnum.DELETED_BY_CUSTOMER.getCode());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateByConversationId(conversation);
            
            // 3. 清理群聊成员缓存
            groupMemberCacheManager.clearGroupMemberCache(conversationId);
            
            log.info("成功删除聊天窗口: {} -> {}", conversationId, customerId);
            return true;
            
        } catch (Exception e) {
            log.error("删除聊天窗口失败: {} -> {}", conversationId, customerId, e);
            return false;
        }
    }
    
    /**
     * 软删除聊天窗口（客服主动删除/归档）
     * 自动清理群聊成员缓存
     * 
     * @param conversationId 聊天窗口ID
     * @param agentId 客服ID
     * @return 是否成功删除
     */
    @Transactional
    public boolean deleteConversationByAgent(String conversationId, String agentId) {
        try {
            log.info("客服删除聊天窗口: {} -> {}", conversationId, agentId);
            
            // 1. 检查客服是否在群中
            if (!groupMemberCacheManager.isGroupMember(conversationId, agentId)) {
                log.warn("客服不在聊天窗口中，无法删除: {} -> {}", conversationId, agentId);
                return false;
            }
            
            // 2. 更新聊天窗口状态为已删除
            ChatConversationDO conversation = new ChatConversationDO();
            conversation.setConversationId(conversationId);
            conversation.setStatus(ConversationStatusEnum.DELETED_BY_AGENT.getCode());
            conversation.setUpdatedAt(new Date());
            conversationMapper.updateByConversationId(conversation);
            
            // 3. 清理群聊成员缓存
            groupMemberCacheManager.clearGroupMemberCache(conversationId);
            
            log.info("成功删除聊天窗口: {} -> {}", conversationId, agentId);
            return true;
            
        } catch (Exception e) {
            log.error("删除聊天窗口失败: {} -> {}", conversationId, agentId, e);
            return false;
        }
    }
    
    /**
     * 获取聊天窗口成员列表
     * 使用缓存查询，性能优化
     * 
     * @param conversationId 聊天窗口ID
     * @return 成员列表
     */
    public List<ChatConversationMemberDO> getChatWindowMembers(String conversationId) {
        return groupMemberCacheManager.getGroupMembers(conversationId);
    }
    
    /**
     * 获取聊天窗口成员ID集合
     * 使用缓存查询，性能优化
     * 
     * @param conversationId 聊天窗口ID
     * @return 成员ID集合
     */
    public Set<String> getChatWindowMemberIds(String conversationId) {
        return groupMemberCacheManager.getGroupMemberIds(conversationId);
    }
    
    /**
     * 检查用户是否为聊天窗口成员
     * 使用缓存查询，性能优化
     * 
     * @param conversationId 聊天窗口ID
     * @param memberId 成员ID
     * @return 是否为成员
     */
    public boolean isChatWindowMember(String conversationId, String memberId) {
        return groupMemberCacheManager.isGroupMember(conversationId, memberId);
    }
    
    /**
     * 获取聊天窗口成员数量
     * 使用缓存查询，性能优化
     * 
     * @param conversationId 聊天窗口ID
     * @return 成员数量
     */
    public int getChatWindowMemberCount(String conversationId) {
        return groupMemberCacheManager.getGroupMemberCount(conversationId);
    }
    
    /**
     * 刷新聊天窗口成员缓存
     * 当需要强制刷新缓存时使用
     * 
     * @param conversationId 聊天窗口ID
     */
    public void refreshChatWindowMemberCache(String conversationId) {
        groupMemberCacheManager.refreshGroupMemberCache(conversationId);
    }
}
