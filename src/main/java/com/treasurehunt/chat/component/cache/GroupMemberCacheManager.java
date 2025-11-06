package com.treasurehunt.chat.component.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import com.treasurehunt.chat.mapper.ChatConversationMemberMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 群聊成员缓存管理器
 * 使用Redis缓存群聊成员信息，提供高效的读写操作
 * 
 * 缓存策略：
 * 1. 读操作：先查Redis缓存，缓存未命中再查数据库
 * 2. 写操作：同时更新Redis缓存和数据库
 * 3. 缓存过期时间：30分钟
 */
@Slf4j
@Service
public class GroupMemberCacheManager {
    
    @Autowired
    private StatefulRedisConnection<String, String> redisConnection;
    
    @Autowired
    private ChatConversationMemberMapper conversationMemberMapper;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Redis缓存key前缀
     */
    private static final String CACHE_KEY_PREFIX = "chat:group:members:";
    
    /**
     * 缓存过期时间（分钟）
     */
    private static final long CACHE_EXPIRE_MINUTES = 30;
    
    /**
     * 获取群聊成员列表
     * 先查Redis缓存，缓存未命中再查数据库
     * 
     * @param conversationId 会话ID
     * @return 群聊成员列表
     */
    public List<ChatConversationMemberDO> getGroupMembers(String conversationId) {
        String cacheKey = buildCacheKey(conversationId);
        
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            
            // 1. 先查Redis缓存
            String cachedData = commands.get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取群聊成员: {}", conversationId);
                return objectMapper.readValue(cachedData, new TypeReference<List<ChatConversationMemberDO>>() {});
            }
            
            // 2. 缓存未命中，查数据库
            log.debug("缓存未命中，从数据库查询群聊成员: {}", conversationId);
            List<ChatConversationMemberDO> members = conversationMemberMapper.selectByConversationId(conversationId);
            
            // 3. 将结果写入缓存
            if (members != null && !members.isEmpty()) {
                String jsonData = objectMapper.writeValueAsString(members);
                commands.setex(cacheKey, CACHE_EXPIRE_MINUTES * 60, jsonData);
                log.debug("群聊成员数据已缓存: {}, 成员数量: {}", conversationId, members.size());
            }
            
            return members;
            
        } catch (Exception e) {
            log.error("获取群聊成员失败: {}", conversationId, e);
            // 缓存异常时，直接查数据库
            return conversationMemberMapper.selectByConversationId(conversationId);
        }
    }
    
    /**
     * 获取群聊成员ID集合
     * 先查Redis缓存，缓存未命中再查数据库
     * 
     * @param conversationId 会话ID
     * @return 群聊成员ID集合
     */
    public Set<String> getGroupMemberIds(String conversationId) {
        String cacheKey = buildCacheKey(conversationId) + ":ids";
        
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            
            // 1. 先查Redis缓存
            String cachedData = commands.get(cacheKey);
            if (cachedData != null) {
                log.debug("从缓存获取群聊成员ID: {}", conversationId);
                return objectMapper.readValue(cachedData, new TypeReference<Set<String>>() {});
            }
            
            // 2. 缓存未命中，查数据库
            log.debug("缓存未命中，从数据库查询群聊成员ID: {}", conversationId);
            List<ChatConversationMemberDO> members = conversationMemberMapper.selectByConversationId(conversationId);
            Set<String> memberIds = members.stream()
                    .map(ChatConversationMemberDO::getMemberId)
                    .collect(Collectors.toSet());
            
            // 3. 将结果写入缓存
            if (!memberIds.isEmpty()) {
                String jsonData = objectMapper.writeValueAsString(memberIds);
                commands.setex(cacheKey, CACHE_EXPIRE_MINUTES * 60, jsonData);
                log.debug("群聊成员ID已缓存: {}, 成员数量: {}", conversationId, memberIds.size());
            }
            
            return memberIds;
            
        } catch (Exception e) {
            log.error("获取群聊成员ID失败: {}", conversationId, e);
            // 缓存异常时，直接查数据库
            List<ChatConversationMemberDO> members = conversationMemberMapper.selectByConversationId(conversationId);
            return members.stream()
                    .map(ChatConversationMemberDO::getMemberId)
                    .collect(Collectors.toSet());
        }
    }
    
    /**
     * 添加群聊成员
     * 同时更新Redis缓存和数据库
     * 
     * @param conversationId 会话ID
     * @param member 群聊成员
     */
    public void addGroupMember(String conversationId, ChatConversationMemberDO member) {
        try {
            // 1. 更新数据库
            conversationMemberMapper.insert(member);
            log.info("添加群聊成员到数据库: {} -> {}", conversationId, member.getMemberId());
            
            // 2. 更新缓存
            refreshGroupMemberCache(conversationId);
            
        } catch (Exception e) {
            log.error("添加群聊成员失败: {} -> {}", conversationId, member.getMemberId(), e);
            throw new RuntimeException("添加群聊成员失败", e);
        }
    }
    
    /**
     * 移除群聊成员
     * 同时更新Redis缓存和数据库
     * 
     * @param conversationId 会话ID
     * @param memberId 成员ID
     */
    public void removeGroupMember(String conversationId, String memberId) {
        try {
            // 1. 更新数据库
            conversationMemberMapper.deleteByConversationIdAndUserId(conversationId, memberId);
            log.info("从数据库移除群聊成员: {} -> {}", conversationId, memberId);
            
            // 2. 更新缓存
            refreshGroupMemberCache(conversationId);
            
        } catch (Exception e) {
            log.error("移除群聊成员失败: {} -> {}", conversationId, memberId, e);
            throw new RuntimeException("移除群聊成员失败", e);
        }
    }
    
    /**
     * 批量添加群聊成员
     * 同时更新Redis缓存和数据库
     * 
     * @param conversationId 会话ID
     * @param members 群聊成员列表
     */
    public void addGroupMembers(String conversationId, List<ChatConversationMemberDO> members) {
        try {
            // 1. 批量插入数据库（使用MyBatis-Plus的批量插入）
            conversationMemberMapper.insertBatch(members);
            log.info("批量添加群聊成员到数据库: {} -> 数量: {}", conversationId, members.size());
            
            // 2. 更新缓存
            refreshGroupMemberCache(conversationId);
            
        } catch (Exception e) {
            log.error("批量添加群聊成员失败: {} -> 数量: {}", conversationId, members.size(), e);
            throw new RuntimeException("批量添加群聊成员失败", e);
        }
    }
    
    /**
     * 批量移除群聊成员
     * 同时更新Redis缓存和数据库
     * 
     * @param conversationId 会话ID
     * @param memberIds 成员ID列表
     */
    public void removeGroupMembers(String conversationId, List<String> memberIds) {
        try {
            // 1. 批量删除数据库（使用MyBatis-Plus的批量删除）
            conversationMemberMapper.deleteBatchByConversationIdAndMemberIds(conversationId, memberIds);
            log.info("批量移除群聊成员从数据库: {} -> 数量: {}", conversationId, memberIds.size());
            
            // 2. 更新缓存
            refreshGroupMemberCache(conversationId);
            
        } catch (Exception e) {
            log.error("批量移除群聊成员失败: {} -> 数量: {}", conversationId, memberIds.size(), e);
            throw new RuntimeException("批量移除群聊成员失败", e);
        }
    }
    
    /**
     * 检查用户是否为群聊成员
     * 
     * @param conversationId 会话ID
     * @param memberId 成员ID
     * @return 是否为群聊成员
     */
    public boolean isGroupMember(String conversationId, String memberId) {
        Set<String> memberIds = getGroupMemberIds(conversationId);
        return memberIds.contains(memberId);
    }
    
    /**
     * 获取群聊成员数量
     * 
     * @param conversationId 会话ID
     * @return 群聊成员数量
     */
    public int getGroupMemberCount(String conversationId) {
        Set<String> memberIds = getGroupMemberIds(conversationId);
        return memberIds.size();
    }
    
    /**
     * 刷新群聊成员缓存
     * 从数据库重新加载数据并更新缓存
     * 
     * @param conversationId 会话ID
     */
    public void refreshGroupMemberCache(String conversationId) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            
            // 1. 删除旧缓存
            String cacheKey = buildCacheKey(conversationId);
            String idsCacheKey = cacheKey + ":ids";
            commands.del(cacheKey);
            commands.del(idsCacheKey);
            
            // 2. 重新查询数据库
            List<ChatConversationMemberDO> members = conversationMemberMapper.selectByConversationId(conversationId);
            
            // 3. 更新缓存
            if (members != null && !members.isEmpty()) {
                String membersJson = objectMapper.writeValueAsString(members);
                commands.setex(cacheKey, CACHE_EXPIRE_MINUTES * 60, membersJson);
                
                Set<String> memberIds = members.stream()
                        .map(ChatConversationMemberDO::getMemberId)
                        .collect(Collectors.toSet());
                String idsJson = objectMapper.writeValueAsString(memberIds);
                commands.setex(idsCacheKey, CACHE_EXPIRE_MINUTES * 60, idsJson);
                
                log.info("群聊成员缓存已刷新: {}, 成员数量: {}", conversationId, members.size());
            }
            
        } catch (Exception e) {
            log.error("刷新群聊成员缓存失败: {}", conversationId, e);
        }
    }
    
    /**
     * 清除群聊成员缓存
     * 
     * @param conversationId 会话ID
     */
    public void clearGroupMemberCache(String conversationId) {
        try {
            RedisCommands<String, String> commands = redisConnection.sync();
            String cacheKey = buildCacheKey(conversationId);
            String idsCacheKey = cacheKey + ":ids";
            commands.del(cacheKey);
            commands.del(idsCacheKey);
            log.info("群聊成员缓存已清除: {}", conversationId);
        } catch (Exception e) {
            log.error("清除群聊成员缓存失败: {}", conversationId, e);
        }
    }
    
    /**
     * 构建缓存key
     * 
     * @param conversationId 会话ID
     * @return 缓存key
     */
    private String buildCacheKey(String conversationId) {
        return CACHE_KEY_PREFIX + conversationId;
    }
}
