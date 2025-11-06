package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;

import java.util.List;

/**
 * 会话成员 Mapper（MyBatis-Plus）
 * CRUD 统一以 conversation_id 作为业务主键维度
 */
@Mapper
public interface ChatConversationMemberMapper extends BaseMapper<ChatConversationMemberDO> {
    
    /**
     * 根据会话ID查询成员列表
     * 
     * @param conversationId 会话ID
     * @return 成员列表
     */
    @Select("SELECT * FROM chat_conversation_member WHERE conversation_id = #{conversationId}")
    List<ChatConversationMemberDO> selectByConversationId(@Param("conversationId") String conversationId);
    
    /**
     * 根据会话ID和成员ID删除成员
     * 
     * @param conversationId 会话ID
     * @param memberId 成员ID
     * @return 删除的记录数
     */
    @Delete("DELETE FROM chat_conversation_member WHERE conversation_id = #{conversationId} AND member_id = #{memberId}")
    int deleteByConversationIdAndUserId(@Param("conversationId") String conversationId, @Param("memberId") String memberId);
    
    /**
     * 根据会话ID查询成员数量
     * 
     * @param conversationId 会话ID
     * @return 成员数量
     */
    @Select("SELECT COUNT(*) FROM chat_conversation_member WHERE conversation_id = #{conversationId}")
    int countByConversationId(@Param("conversationId") String conversationId);
    
    /**
     * 检查用户是否为会话成员
     * 
     * @param conversationId 会话ID
     * @param memberId 成员ID
     * @return 是否存在
     */
    @Select("SELECT COUNT(*) > 0 FROM chat_conversation_member WHERE conversation_id = #{conversationId} AND member_id = #{memberId}")
    boolean existsByConversationIdAndUserId(@Param("conversationId") String conversationId, @Param("memberId") String memberId);
    
    /**
     * 批量插入会话成员
     * 
     * @param members 成员列表
     * @return 插入的记录数
     */
    @Insert("<script>" +
            "INSERT INTO chat_conversation_member (conversation_id, member_type, member_id, joined_at) VALUES " +
            "<foreach collection='members' item='member' separator=','>" +
            "(#{member.conversationId}, #{member.memberType}, #{member.memberId}, #{member.joinedAt})" +
            "</foreach>" +
            "</script>")
    int insertBatch(@Param("members") List<ChatConversationMemberDO> members);
    
    /**
     * 批量删除会话成员
     * 
     * @param conversationId 会话ID
     * @param memberIds 成员ID列表
     * @return 删除的记录数
     */
    @Delete("<script>" +
            "DELETE FROM chat_conversation_member WHERE conversation_id = #{conversationId} AND member_id IN " +
            "<foreach collection='memberIds' item='memberId' open='(' separator=',' close=')'>" +
            "#{memberId}" +
            "</foreach>" +
            "</script>")
    int deleteBatchByConversationIdAndMemberIds(@Param("conversationId") String conversationId, @Param("memberIds") List<String> memberIds);
}


