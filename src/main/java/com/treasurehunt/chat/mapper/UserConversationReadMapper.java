package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.UserConversationReadDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 已读指针 Mapper（MyBatis-Plus）
 * CRUD 统一以 conversation_id 作为业务查询条件
 */
@Mapper
public interface UserConversationReadMapper extends BaseMapper<UserConversationReadDO> {
}


