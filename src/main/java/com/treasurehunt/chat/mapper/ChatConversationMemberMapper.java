package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatConversationMemberDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 会话成员 Mapper（MyBatis-Plus）
 * 查询与变更须带 {@code business_line}，与 {@code chat_conversation} 隔离一致。
 */
@Mapper
public interface ChatConversationMemberMapper extends BaseMapper<ChatConversationMemberDO> {

    @Select("SELECT * FROM chat_conversation_member WHERE conversation_id = #{conversationId} "
            + "AND business_line = #{businessLine}")
    List<ChatConversationMemberDO> selectByConversationIdAndBusinessLine(@Param("conversationId") String conversationId,
                                                                           @Param("businessLine") String businessLine);

    @Delete("DELETE FROM chat_conversation_member WHERE conversation_id = #{conversationId} "
            + "AND business_line = #{businessLine} AND member_id = #{memberId}")
    int deleteByConversationIdBusinessLineAndMemberId(@Param("conversationId") String conversationId,
                                                       @Param("businessLine") String businessLine,
                                                       @Param("memberId") String memberId);

    @Select("SELECT COUNT(*) FROM chat_conversation_member WHERE conversation_id = #{conversationId} "
            + "AND business_line = #{businessLine}")
    int countByConversationIdAndBusinessLine(@Param("conversationId") String conversationId,
                                                @Param("businessLine") String businessLine);

    @Select("SELECT COUNT(*) > 0 FROM chat_conversation_member WHERE conversation_id = #{conversationId} "
            + "AND business_line = #{businessLine} AND member_id = #{memberId}")
    boolean existsByConversationIdBusinessLineAndMemberId(@Param("conversationId") String conversationId,
                                                           @Param("businessLine") String businessLine,
                                                           @Param("memberId") String memberId);

    @Insert("<script>"
            + "INSERT INTO chat_conversation_member (conversation_id, business_line, member_type, member_id, joined_at) VALUES "
            + "<foreach collection='members' item='member' separator=','>"
            + "(#{member.conversationId}, #{member.businessLine}, #{member.memberType}, #{member.memberId}, #{member.joinedAt})"
            + "</foreach>"
            + "</script>")
    int insertBatch(@Param("members") List<ChatConversationMemberDO> members);

    @Delete("<script>"
            + "DELETE FROM chat_conversation_member WHERE conversation_id = #{conversationId} "
            + "AND business_line = #{businessLine} AND member_id IN "
            + "<foreach collection='memberIds' item='memberId' open='(' separator=',' close=')'>"
            + "#{memberId}"
            + "</foreach>"
            + "</script>")
    int deleteBatchByConversationIdBusinessLineAndMemberIds(@Param("conversationId") String conversationId,
                                                             @Param("businessLine") String businessLine,
                                                             @Param("memberIds") List<String> memberIds);

    /**
     * 参与会话列表：当前客服在成员表中且未退群，且不是该会话主接待（主接待走 {@code chat_conversation.agent_id} 查询）。
     */
    @Select("SELECT c.* FROM chat_conversation c "
            + "INNER JOIN chat_conversation_member m ON c.conversation_id = m.conversation_id "
            + "AND c.business_line = m.business_line "
            + "WHERE m.business_line = #{businessLine} AND m.member_type = 'agent' AND m.member_id = #{agentId} "
            + "AND m.left_at IS NULL "
            + "AND (c.agent_id IS NULL OR c.agent_id <> #{agentId}) "
            + "AND c.status IN ('active', 'waiting') "
            + "ORDER BY c.updated_at DESC")
    List<ChatConversationDO> selectParticipantConversationsForAgent(@Param("businessLine") String businessLine,
                                                                      @Param("agentId") String agentId);
}
