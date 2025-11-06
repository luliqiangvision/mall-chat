package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.ChatAgentDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 客服信息 Mapper（MyBatis-Plus）
 * 负责客服信息的CRUD操作
 */
@Mapper
public interface ChatAgentMapper extends BaseMapper<ChatAgentDO> {
    
    /**
     * 根据客服类型和状态查询客服列表
     * 
     * @param agentType 客服类型
     * @param status 客服状态
     * @param tenantId 租户ID
     * @return 客服列表
     */
    @Select("SELECT * FROM chat_agent WHERE agent_type = #{agentType} AND status = #{status} AND tenant_id = #{tenantId}")
    List<ChatAgentDO> selectByTypeAndStatus(@Param("agentType") String agentType, 
                                          @Param("status") String status, 
                                          @Param("tenantId") Long tenantId);
    
    /**
     * 查询售前客服列表（活跃状态）
     * 
     * @param tenantId 租户ID
     * @return 售前客服列表
     */
    @Select("SELECT * FROM chat_agent WHERE agent_type = 'pre-sales' AND status = 'active' AND tenant_id = #{tenantId}")
    List<ChatAgentDO> selectPreSalesAgents(@Param("tenantId") Long tenantId);
    
    /**
     * 查询售后客服列表（活跃状态）
     * 
     * @param tenantId 租户ID
     * @return 售后客服列表
     */
    @Select("SELECT * FROM chat_agent WHERE agent_type = 'after-sales' AND status = 'active' AND tenant_id = #{tenantId}")
    List<ChatAgentDO> selectAfterSalesAgents(@Param("tenantId") Long tenantId);
    
    /**
     * 根据客服ID查询客服信息
     * 
     * @param agentId 客服ID
     * @return 客服信息
     */
    @Select("SELECT * FROM chat_agent WHERE agent_id = #{agentId}")
    ChatAgentDO selectByAgentId(@Param("agentId") String agentId);
    
    /**
     * 根据客服名称查询客服信息
     * 
     * @param agentName 客服名称
     * @return 客服信息
     */
    @Select("SELECT * FROM chat_agent WHERE agent_name = #{agentName}")
    ChatAgentDO selectByAgentName(@Param("agentName") String agentName);
    
    /**
     * 更新客服当前会话数
     * 
     * @param agentId 客服ID
     * @param currentConversations 当前会话数
     * @return 更新的记录数
     */
    @Update("UPDATE chat_agent SET current_conversations = #{currentConversations}, updated_at = NOW() WHERE agent_id = #{agentId}")
    int updateCurrentConversations(@Param("agentId") String agentId, @Param("currentConversations") Integer currentConversations);
    
    /**
     * 更新客服状态
     * 
     * @param agentId 客服ID
     * @param status 客服状态
     * @return 更新的记录数
     */
    @Update("UPDATE chat_agent SET status = #{status}, updated_at = NOW() WHERE agent_id = #{agentId}")
    int updateAgentStatus(@Param("agentId") String agentId, @Param("status") String status);
    
    /**
     * 查询负载最低的售前客服
     * 
     * @param tenantId 租户ID
     * @return 负载最低的售前客服
     */
    @Select("SELECT * FROM chat_agent WHERE agent_type = 'pre-sales' AND status = 'active' AND tenant_id = #{tenantId} " +
            "AND current_conversations < max_concurrent_conversations " +
            "ORDER BY current_conversations ASC LIMIT 1")
    ChatAgentDO selectLeastLoadedPreSalesAgent(@Param("tenantId") Long tenantId);
}
