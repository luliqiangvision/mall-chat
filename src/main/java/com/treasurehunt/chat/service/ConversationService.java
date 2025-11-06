package com.treasurehunt.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.treasurehunt.chat.domain.ChatConversationDO;
import com.treasurehunt.chat.domain.ChatMessageDO;
import com.treasurehunt.chat.domain.MallShopDO;
import com.treasurehunt.chat.mapper.ChatConversationMapper;
import com.treasurehunt.chat.mapper.ChatMessageMapper;
import com.treasurehunt.chat.vo.ConversationCheckRequest;
import com.treasurehunt.chat.vo.ConversationCheckResponse;
import com.treasurehunt.chat.vo.MallShopVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 会话管理服务
 * 专门处理会话相关的HTTP业务逻辑
 */
@Slf4j
@Service
public class ConversationService {
    
    @Autowired
    private ChatConversationMapper conversationMapper;
    
    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private MallShopService mallShopService;
    
    /**
     * 检查用户在指定店铺是否有历史会话
     * 
     * @param request 预检请求
     * @return 预检响应
     */
    public ConversationCheckResponse checkConversation(ConversationCheckRequest request) {
        try {
            log.debug("检查用户会话: userId={}, shopId={}", request.getUserId(), request.getShopId());
            
            // 查询店铺信息
            MallShopDO shop = mallShopService.getShopById(request.getShopId());
            
            // 查询用户在指定店铺的最新会话
            ChatConversationDO conversation = conversationMapper.findLatestByCustomerAndShop(
                    request.getUserId(), request.getShopId());
            
            ConversationCheckResponse response = new ConversationCheckResponse();
            
            // 设置店铺信息
            if (shop != null) {
                MallShopVO shopVO = MallShopVO.builder()
                        .id(shop.getId())
                        .tenantId(shop.getTenantId())
                        .shopName(shop.getShopName())
                        .shopStatus(shop.getShopStatus())
                        .shopIcon(shop.getShopIcon())
                        .contactPhone(shop.getContactPhone())
                        .build();
                response.setShop(shopVO);
            }
            
            if (conversation != null) {
                // 有历史会话，查询该会话的最新消息ID
                Long latestServerMsgId = getLatestServerMsgIdByConversationId(conversation.getConversationId());
                
                response.setHasConversation(true);
                response.setConversationId(conversation.getConversationId());
                response.setClientMaxServerMsgId(latestServerMsgId);
                
                log.info("找到历史会话: conversationId={}, latestServerMsgId={}", 
                        conversation.getConversationId(), latestServerMsgId);
            } else {
                // 无历史会话
                response.setHasConversation(false);
                log.info("用户在该店铺无历史会话: userId={}, shopId={}", 
                        request.getUserId(), request.getShopId());
            }
            
            return response;
            
        } catch (Exception e) {
            log.error("检查会话失败: userId={}, shopId={}", request.getUserId(), request.getShopId(), e);
            // 出错时返回无会话，让前端正常进入
            ConversationCheckResponse response = new ConversationCheckResponse();
            response.setHasConversation(false);
            return response;
        }
    }
    
    /**
     * 获取会话的最新服务端消息ID
     * 
     * @param conversationId 会话ID
     * @return 最新服务端消息ID
     */
    private Long getLatestServerMsgIdByConversationId(String conversationId) {
        try {
            QueryWrapper<ChatMessageDO> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("conversation_id", conversationId)
                    .orderByDesc("server_msg_id")
                    .last("LIMIT 1");
            
            ChatMessageDO latestMessage = chatMessageMapper.selectOne(queryWrapper);
            return latestMessage != null ? latestMessage.getServerMsgId() : 0L;
            
        } catch (Exception e) {
            log.error("获取会话最新消息ID失败: conversationId={}", conversationId, e);
            return 0L;
        }
    }
}
