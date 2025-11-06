package com.treasurehunt.chat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.treasurehunt.chat.domain.ChatMessageDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息 Mapper（MyBatis-Plus）
 * CRUD 统一以 conversation_id 作为业务主键维度
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
    /**
     * 查询会话内最大 server_msg_id
     */
    Long getMaxServerMsgIdByConvId(@Param("conversationId") String conversationId);

    /**
     * 按 (conversation_id, client_msg_id) 查询一条消息（用于幂等查询）
     */
    ChatMessageDO selectByConvIdAndClientMsgId(@Param("conversationId") String conversationId,
                                               @Param("clientMsgId") String clientMsgId);

    /**
     * 常规插入（Redis 正常时使用，若唯一键冲突则上层做幂等处理）
     */
    int insertOne(ChatMessageDO entity);

    /**
     * 降级插入：使用 ON DUPLICATE KEY UPDATE server_msg_id = server_msg_id + 1
     * 牺牲幂等性，由数据库自增 server_msg_id。
     */
    int insertOneOnDupIncr(ChatMessageDO entity);

    /**
     * 注解版降级插入：ON DUPLICATE KEY 自增 server_msg_id
     */
    @Insert("INSERT INTO chat_message(\n" +
            "  conversation_id, server_msg_id, client_msg_id, sender_id, from_user_id, msg_type, content, payload_json, hash_code, status, push_attempts, shop_id, created_at, delivered_at\n" +
            ") VALUES (\n" +
            "  #{conversationId}, #{serverMsgId}, #{clientMsgId}, #{senderId}, #{fromUserId}, #{msgType}, #{content}, #{payloadJson}, #{hashCode}, #{status}, #{pushAttempts}, #{shopId}, #{createdAt}, #{deliveredAt}\n" +
            ")\n" +
            "ON DUPLICATE KEY UPDATE\n" +
            "  server_msg_id = server_msg_id + 1,\n" +
            "  content = VALUES(content),\n" +
            "  payload_json = VALUES(payload_json),\n" +
            "  status = VALUES(status)")
    int insertOneOnDupIncrAnno(ChatMessageDO entity);

    /**
     * 批量查询多个会话的最新 server_msg_id
     * @param conversationIds 会话ID列表
     * @return List<Map> key: conversation_id, server_msg_id (可能为null表示无消息)
     */
    @Select("<script>" +
            "SELECT conversation_id, MAX(server_msg_id) as server_msg_id " +
            "FROM chat_message " + 
            "WHERE conversation_id IN " +
            "<foreach collection='conversationIds' item='id' open='(' close=')' separator=','>" + 
            "#{id}" +
            "</foreach> " +
            "GROUP BY conversation_id" +
            "</script>")
    List<Map<String, Object>> getMaxServerMsgIdBatch(@Param("conversationIds") List<String> conversationIds);
}


