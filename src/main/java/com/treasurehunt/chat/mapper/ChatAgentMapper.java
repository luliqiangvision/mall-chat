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
     * 按统一身份ID和业务线查询客服身份列表（阶段1：支持1:N）。
     */
    List<ChatAgentDO> selectBySubjectIdAndBusinessLine(@Param("subjectId") Long subjectId,
                                                       @Param("businessLine") String businessLine);
    
    /**
     * 按业务线、店铺、客服类型、状态查询客服列表（经 {@code chat_agent_shop_relation}，不按 tenant 过滤）。
     */
    @Select("SELECT a.* FROM chat_agent a INNER JOIN chat_agent_shop_relation r "
            + "ON r.agent_id = a.agent_id AND r.business_line = a.business_line "
            + "WHERE r.business_line = #{businessLine} AND r.shop_id = #{shopId} AND r.status = 'active' "
            + "AND a.agent_type = #{agentType} AND a.status = #{status}")
    List<ChatAgentDO> selectByBusinessLineShopTypeAndStatus(@Param("businessLine") String businessLine,
                                                            @Param("shopId") Long shopId,
                                                            @Param("agentType") String agentType,
                                                            @Param("status") String status);

    /**
     * 根据业务线和客服ID查询客服信息
     */
    @Select("SELECT * FROM chat_agent WHERE business_line = #{businessLine} AND agent_id = #{agentId}")
    ChatAgentDO selectByBusinessLineAndAgentId(@Param("businessLine") String businessLine,
                                               @Param("agentId") String agentId);

    /**
     * 根据业务线与客服名称查询客服信息（登录名在业务线内唯一）。
     */
    @Select("SELECT * FROM chat_agent WHERE business_line = #{businessLine} AND agent_name = #{agentName}")
    ChatAgentDO selectByBusinessLineAndAgentName(@Param("businessLine") String businessLine,
                                                 @Param("agentName") String agentName);

    /**
     * 更新客服当前会话数（限定业务线，避免跨线误更新）
     */
    @Update("UPDATE chat_agent SET current_conversations = #{currentConversations}, updated_at = NOW() "
            + "WHERE agent_id = #{agentId} AND business_line = #{businessLine}")
    int updateCurrentConversations(@Param("businessLine") String businessLine,
                                   @Param("agentId") String agentId,
                                   @Param("currentConversations") Integer currentConversations);

    /**
     * 更新客服状态（限定业务线）
     */
    @Update("UPDATE chat_agent SET status = #{status}, updated_at = NOW() "
            + "WHERE agent_id = #{agentId} AND business_line = #{businessLine}")
    int updateAgentStatus(@Param("businessLine") String businessLine,
                          @Param("agentId") String agentId,
                          @Param("status") String status);
    
    /**
     * 有 {@code shop_id} 时，客户进线：在 {@code chat_agent_shop_relation} 与 {@code chat_agent} 交集内取负载最低（选人不用 tenant_id）。
     */
    /**
     * 有 shop 时按负载取 1 人（已废弃直连进线，店售前改由 {@link com.treasurehunt.chat.service.AgentManagementService} 池内选人）。
     */
    @Select("SELECT a.* FROM chat_agent a INNER JOIN chat_agent_shop_relation r "
            + "ON r.agent_id = a.agent_id AND r.business_line = a.business_line "
            + "WHERE r.business_line = #{businessLine} AND r.shop_id = #{shopId} AND r.status = 'active' "
            + "AND a.status = 'active' "
            + "ORDER BY a.current_conversations ASC LIMIT 1")
    ChatAgentDO selectLeastLoadedAgentForShop(@Param("businessLine") String businessLine,
                                               @Param("shopId") Long shopId);

    /**
     * 无 shop 时：按业务线 + {@code agent_type} 取 {@code current_conversations} 最低（不因会话数达 max 而排除）。
     */
    @Select("SELECT * FROM chat_agent WHERE business_line = #{businessLine} "
            + "AND agent_type = #{agentType} AND status = 'active' "
            + "ORDER BY current_conversations ASC LIMIT 1")
    ChatAgentDO selectLeastLoadedAgentByBusinessLineAndType(@Param("businessLine") String businessLine,
                                                             @Param("agentType") String agentType);

    /**
     * 当前客服在业务线下绑定的有效店铺 ID（{@code chat_agent_shop_relation.status=active}）。
     */
    @Select("SELECT DISTINCT r.shop_id FROM chat_agent_shop_relation r "
            + "WHERE r.business_line = #{businessLine} AND r.agent_id = #{agentId} AND r.status = 'active'")
    List<Long> selectActiveShopIdsByBusinessLineAndAgent(@Param("businessLine") String businessLine,
                                                          @Param("agentId") String agentId);

}
