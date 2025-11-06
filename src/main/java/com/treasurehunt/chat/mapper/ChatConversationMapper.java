package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 会话窗口 Mapper（MyBatis-Plus）
 * CRUD 以 conversation_id 作为业务主键（逻辑主键），id 仅占位
 */
@Mapper
public interface ChatConversationMapper extends BaseMapper<ChatConversationDO> {
    
    /**
     * 根据会话ID更新会话信息
     * 
     * @param conversation 会话信息
     * @return 更新的记录数
     */
    @Update("UPDATE chat_conversation SET status = #{status}, updated_at = #{updatedAt} WHERE conversation_id = #{conversationId}")
    int updateByConversationId(@Param("conversation") ChatConversationDO conversation);
    
    /**
     * 查询用户在指定店铺的最新会话
     * 
     * @param customerId 客户ID
     * @param shopId 店铺ID
     * @return 会话信息
     */
    @Select("SELECT * FROM chat_conversation WHERE customer_id = #{customerId} AND shop_id = #{shopId} " +
            "AND status NOT IN ('deleted_by_customer', 'deleted_by_agent') " +
            "ORDER BY created_at DESC LIMIT 1")
    ChatConversationDO findLatestByCustomerAndShop(@Param("customerId") String customerId, @Param("shopId") Long shopId);
}

