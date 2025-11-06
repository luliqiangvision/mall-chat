-- =============================================
-- Mall Chat 数据库初始化脚本
-- =============================================

-- 1. 聊天会话表
CREATE TABLE chat_conversation (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id VARCHAR(128) NOT NULL UNIQUE COMMENT '外部可用 uuid',
  customer_id BIGINT NOT NULL COMMENT '客户用户ID（发起方）',
  agent_id BIGINT NULL COMMENT '分配的客服座席ID（可为空，未分配时为null）',
  status VARCHAR(16) NOT NULL DEFAULT 'waiting' COMMENT '会话状态：waiting-等待客服响应(群聊里还没有人类客服),active-正常活跃会话(还在聊天),closed-会话关闭(客服询问客户是否还有问题,客服手动关闭),deleted_by_customer-客户删除会话(软删除),deleted_by_agent-客服删除会话(预留,主要是有人来骚扰,拉黑用的)',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '多租户标识，亦即商户/店铺ID',
  shop_id BIGINT NULL COMMENT '店铺ID（可为空）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant_status (tenant_id, status),
  UNIQUE KEY uk_customer_shop (customer_id, shop_id),
  UNIQUE KEY uk_agentid_conversation_id (agent_id, conversation_id),
  INDEX idx_customer_shop_status (customer_id, shop_id,status)
) ENGINE=InnoDB COMMENT='聊天会话表';

-- 2. 会话成员表（群聊模式）
CREATE TABLE chat_conversation_member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id VARCHAR(128) NOT NULL COMMENT '会话ID（与 chat_conversation.conversation_id 关联）',
  member_type VARCHAR(16) NOT NULL COMMENT '成员类型：customer-客户,agent-客服,system-系统',
  member_id VARCHAR(64) NOT NULL COMMENT '成员ID（用户ID或系统标识）',
  joined_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  left_at DATETIME NULL COMMENT '离开时间',
  INDEX idx_conversation_member (conversation_id, member_type),
  INDEX idx_member_active (member_id, left_at),
  UNIQUE KEY uq_conversation_member_active (conversation_id, member_type, member_id, left_at)
) ENGINE=InnoDB COMMENT='会话成员表（群聊模式）';

-- 3. 聊天消息表
CREATE TABLE chat_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  conversation_id VARCHAR(128) NOT NULL COMMENT '会话ID（包含分片键，例如 "shardKey:convX"）',
  server_msg_id BIGINT NOT NULL COMMENT '会话内递增序号',
  client_msg_id VARCHAR(128) NOT NULL COMMENT '客户端消息ID（幂等）',
  sender_id VARCHAR(64) NOT NULL COMMENT '发送者ID,如果是转发,那么这个字段就是转发人的id',
  from_user_id VARCHAR(64) NOT NULL COMMENT '来源用户ID',
  msg_type VARCHAR(32) NOT NULL COMMENT '消息类型',
  content TEXT NULL COMMENT '文本内容（纯文本消息的正文）',
  payload_json JSON NULL COMMENT '业务载荷JSON',
  hash_code BINARY(32) NULL COMMENT '媒体哈希（图片/视频等，原始SHA-256 32字节）',
  status VARCHAR(16) DEFAULT 'PENDING' COMMENT '消息状态：PENDING-待推送,PUSHED-已推送,DELIVERED-已送达,READ-已读,FAILED-失败',
  push_attempts INT DEFAULT 0 COMMENT '推送重试次数',
  shop_id BIGINT NOT NULL COMMENT '店铺ID（可为空）',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  delivered_at DATETIME NULL COMMENT '送达时间',
  INDEX idx_conv_server (conversation_id, server_msg_id),
  INDEX idx_conv_hash (conversation_id, hash_code),
  INDEX idx_sender_time (sender_id, created_at),
  INDEX idx_shop_id (shop_id),
  UNIQUE KEY uq_conv_client (conversation_id, client_msg_id),
  UNIQUE KEY uq_conv_server (conversation_id, server_msg_id)
) ENGINE=InnoDB COMMENT='聊天消息表';

-- 4. 用户会话已读指针表
CREATE TABLE user_conversation_read (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '逻辑主键ID（不参与查询）',
  conversation_id VARCHAR(128) NOT NULL COMMENT '会话ID',
  user_id  BIGINT NULL COMMENT '客户用户ID（纯数字类型，性能更好）',
  agent_id BIGINT NULL COMMENT '客服ID（纯数字类型，性能更好）',
  last_read_server_msg_id BIGINT DEFAULT 0 COMMENT '已读指针（会话内最大已读 server_msg_id）',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_conversation_read_user_conv (user_id, conversation_id),
  UNIQUE KEY uk_agent_conversation (agent_id, conversation_id)
) ENGINE=InnoDB COMMENT='用户会话已读指针表';

-- 5. 客服信息表
CREATE TABLE chat_agent (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  agent_id BIGINT NOT NULL COMMENT '客服ID（唯一标识）',
  agent_name VARCHAR(128) NOT NULL COMMENT '客服姓名',
  password VARCHAR(255) NOT NULL COMMENT '密码（BCrypt加密）',
  agent_type VARCHAR(16) NOT NULL DEFAULT 'pre-sales' COMMENT '客服类型：pre-sales-售前客服,after-sales-售后客服,system-系统客服',
  status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '客服状态：active-活跃,inactive-非活跃,offline-离线',
  max_concurrent_conversations INT DEFAULT 10 COMMENT '最大并发会话数',
  current_conversations INT DEFAULT 0 COMMENT '当前会话数',
  tenant_id BIGINT NOT NULL DEFAULT 1 COMMENT '多租户标识，亦即商户/店铺ID',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant_type_status (tenant_id, agent_type, status),
  INDEX idx_agent_status (agent_id, status),
  INDEX idx_type_status (agent_type, status)
) ENGINE=InnoDB COMMENT='客服信息表';

-- 6. 店铺信息表
CREATE TABLE mall_shop (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '店铺主键（自增ID）',
  tenant_id BIGINT NOT NULL COMMENT '租户ID（所属商户）',
  shop_name VARCHAR(128) NOT NULL COMMENT '店铺名称',
  shop_status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT '店铺状态：active-活跃,inactive-禁用',
  shop_icon VARCHAR(512) NULL COMMENT '店铺图标URL',
  contact_phone VARCHAR(32) NULL COMMENT '联系电话',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_tenant_status (tenant_id, shop_status)
) ENGINE=InnoDB COMMENT='店铺信息表';

-- =============================================
-- 示例数据
-- =============================================

-- 插入示例店铺数据
INSERT INTO mall_shop (tenant_id, shop_name, shop_status, shop_icon, contact_phone) VALUES
(1, '旗舰店', 'active', NULL, '13800138000'),
(1, '北京分店', 'active', NULL, '13800138001'),
(2, '上海总店', 'active', NULL, '13800138002');

-- 插入示例客服数据
-- 密码均为: 123456 (BCrypt加密后的值，实际使用时需要根据BCrypt加密算法生成)
-- 注意：这里使用示例密码哈希值，实际部署时需要替换为真实的BCrypt加密密码
INSERT INTO chat_agent (agent_id, agent_name, password, agent_type, status, max_concurrent_conversations, tenant_id) VALUES
('agent001', '张三', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOslitxEfB7Xi', 'pre-sales', 'active', 10, 1)

-- =============================================
-- 索引说明
-- =============================================

-- chat_conversation 表索引：
-- - idx_tenant_status: 支持按租户和状态查询会话
-- - idx_customer_status: 支持按客户和状态查询会话

-- chat_conversation_member 表索引：
-- - idx_conversation_member: 支持按会话查询成员
-- - idx_member_active: 支持按成员查询活跃会话
-- - uq_conversation_member_active: 确保同一会话中同一成员只能有一个活跃记录

-- chat_message 表索引：
-- - idx_conv_server: 支持按会话和消息序号查询（分页拉取）
-- - idx_conv_hash: 支持按会话和哈希查询（去重）
-- - idx_sender_time: 支持按发送者查询消息历史
-- - uq_conv_client: 确保同一会话中客户端消息ID唯一（幂等）
-- - uq_conv_server: 确保同一会话中服务端消息ID唯一（防止重复）

-- user_conversation_read 表索引：
-- - PRIMARY KEY: id（逻辑主键，不参与查询）
-- - uk_user_conversation_read_user_conv: 保证 (user_id, conversation_id) 组合唯一，便于从用户维度幂等约束
-- - uk_agent_conversation: 保证 (agent_id, conversation_id) 组合唯一，用于客服的已读指针唯一约束

-- chat_agent 表索引：
-- - idx_tenant_type_status: 支持按租户、客服类型和状态查询
-- - idx_agent_status: 支持按客服ID和状态查询
-- - idx_type_status: 支持按客服类型和状态查询

-- mall_shop 表索引：
-- - idx_tenant_status: 支持按租户和状态查询

-- =============================================
-- 注意事项
-- =============================================

-- 1. server_msg_id 在最终设计中建议 NOT NULL（分配后写入），
--    以便 idx_conv_server 索引能被有效利用。
--    ID 孔洞（跳号）是可接受的，未读/分页逻辑需根据 > / >= 而非连续性假设来实现。

-- 2. 数据库层对 (conversation_id, client_msg_id) 建唯一约束，
--    确保消息幂等性。

-- 3. conversation_id 包含分片键，后续可以做分库分表。

-- 4. 文件类消息阶段用 fileHash + clientMsgId 做去重/校验，
--    避免重复上传或重复写库。

-- 5. 降级处理：使用 MySQL 能力自增 server_msg_id，
--    即 INSERT ... ON DUPLICATE KEY UPDATE server_msg_id = server_msg_id + 1，
--    牺牲幂等性；随后再按 (conversation_id, client_msg_id) 查询确认最终 server_msg_id。

