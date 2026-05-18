-- =============================================
-- mall-chat 增量 DDL（在 init.sql 基线之后执行）
-- 执行顺序：先跑 doc/init.sql，再按需追加本文件语句
-- =============================================

-- -----------------------------------------------------------------------------
-- 2026-05-15 客服与店铺多对多关系表（按 shop 路由售前池；索引最左为 business_line，强制查询带业务线）
-- 网关：WebSocket / HTTP 均需在代理层透传 X-Business-Line（与客户 HTTP 一致）
-- -----------------------------------------------------------------------------

CREATE TABLE  chat_agent_shop_relation (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
  shop_id BIGINT NOT NULL COMMENT '店铺业务ID，与 mall_shop.shop_id 一致',
  agent_id VARCHAR(64) NOT NULL COMMENT '客服ID，与 chat_agent.agent_id 一致',
  business_line VARCHAR(64) NOT NULL COMMENT '业务线，与 mall_shop.business_line / 网关 X-Business-Line 枚举名一致',
  tenant_id BIGINT NOT NULL COMMENT '经营主体，冗余自 mall_shop.tenant_id，便于按租户裁剪与统计',
  status VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active-生效, inactive-停用',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  -- 索引最左列为 business_line：查询须带业务线才能走索引，避免跨业务线漏写条件
  UNIQUE KEY uk_bl_shop_agent (business_line, shop_id, agent_id),
  KEY idx_bl_shop_status (business_line, shop_id, status),
  KEY idx_bl_agent_status (business_line, agent_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客服-店铺服务关系（多对多关联表，可配置）';

-- -----------------------------------------------------------------------------
-- 2026-05-16 chat_conversation.tenant_id 允许为空：无店铺进线或店铺未配置 tenant 时不落默认 1
-- -----------------------------------------------------------------------------
ALTER TABLE chat_conversation
  MODIFY COLUMN tenant_id BIGINT NULL DEFAULT NULL COMMENT '经营主体元数据（来自 mall_shop.tenant_id；无则 NULL）';

-- -----------------------------------------------------------------------------
-- 2026-05-17 chat_agent.agent_type：增加 corporate（公司级），无店铺进线或店铺池空时由应用按业务线选用（选人不用 tenant_id）
-- Java：com.treasurehunt.chat.enums.AgentTypeEnum.CORPORATE
-- -----------------------------------------------------------------------------
ALTER TABLE chat_agent
  MODIFY COLUMN agent_type VARCHAR(16) NOT NULL DEFAULT 'pre-sales'
  COMMENT '客服类型：pre-sales-售前, after-sales-售后, corporate-公司级(法务税务等无店铺/兜底), system-系统';

-- -----------------------------------------------------------------------------
-- 2026-05-18 chat_message.business_line：消息落库冗余业务线，无业务线不允许写入
-- -----------------------------------------------------------------------------
ALTER TABLE chat_message
  ADD COLUMN business_line VARCHAR(64) NULL COMMENT '业务线（冗余，与网关 X-Business-Line / chat_conversation.business_line 一致）' AFTER shop_id;

UPDATE chat_message m
  INNER JOIN chat_conversation c ON m.conversation_id = c.conversation_id
  SET m.business_line = c.business_line
  WHERE m.business_line IS NULL
    AND c.business_line IS NOT NULL
    AND TRIM(c.business_line) <> '';

ALTER TABLE chat_message
  MODIFY COLUMN business_line VARCHAR(64) NOT NULL COMMENT '业务线（冗余，落库必填）';

ALTER TABLE chat_message
  ADD INDEX idx_bl_conv_server (business_line, conversation_id, server_msg_id);

-- -----------------------------------------------------------------------------
-- 2026-05-19 chat_conversation_member.business_line：群成员冗余业务线，增删查改须带业务线
-- -----------------------------------------------------------------------------
ALTER TABLE chat_conversation_member
  ADD COLUMN business_line VARCHAR(64) NULL COMMENT '业务线（冗余，与 chat_conversation.business_line 一致）' AFTER conversation_id;

UPDATE chat_conversation_member m
  INNER JOIN chat_conversation c ON m.conversation_id = c.conversation_id
  SET m.business_line = c.business_line
  WHERE m.business_line IS NULL
    AND c.business_line IS NOT NULL
    AND TRIM(c.business_line) <> '';

ALTER TABLE chat_conversation_member
  MODIFY COLUMN business_line VARCHAR(64) NOT NULL COMMENT '业务线（冗余，落库必填）';

ALTER TABLE chat_conversation_member
  ADD INDEX idx_bl_conversation_member (business_line, conversation_id, member_type),
  ADD INDEX idx_bl_member_active (business_line, member_id, member_type, left_at);

-- -----------------------------------------------------------------------------
-- 2026-05-20 chat_message / chat_conversation_member 索引规范：复合索引最左列必须为 business_line
--
-- 【原则】
-- 1. 两表用于检索、排序、唯一约束的复合索引，第一列均为 business_line（与网关 X-Business-Line 一致）。
-- 2. 应用层 SQL / Mapper 增删查改须带 business_line，否则无法有效走索引，且可能跨业务线扫表。
-- 3. init.sql 基线索引不含 business_line，由本段在增量执行时删除并替换；勿在 init.sql 中间插队改索引。
--
-- 【chat_message 目标索引】（以下复合键最左列均为 business_line）
--   uk_bl_conv_server       (business_line, conversation_id, server_msg_id)      — 会话内序号唯一 + 分页拉取
--   idx_bl_conv_hash        (business_line, conversation_id, hash_code)          — 媒体去重
--   idx_bl_sender_time      (business_line, sender_id, created_at)               — 按发送者查历史
--   idx_bl_shop_id          (business_line, shop_id)                             — 按店铺筛消息
--   uk_bl_conv_client       (business_line, conversation_id, client_msg_id)      — 客户端幂等
--
-- 【chat_conversation_member 目标索引】
--   idx_bl_conversation_member       (business_line, conversation_id, member_type)
--   idx_bl_member_active             (business_line, member_id, member_type, left_at)
--   uk_bl_conversation_member_active (business_line, conversation_id, member_type, member_id, left_at)
-- -----------------------------------------------------------------------------
ALTER TABLE chat_message DROP INDEX  idx_conv_server;
ALTER TABLE chat_message DROP INDEX  idx_conv_hash;
ALTER TABLE chat_message DROP INDEX  idx_sender_time;
ALTER TABLE chat_message DROP INDEX  idx_shop_id;
ALTER TABLE chat_message DROP INDEX  uq_conv_client;
ALTER TABLE chat_message DROP INDEX  uq_conv_server;
-- 2026-05-18 非唯一索引，本段统一为 uk_bl_conv_server
ALTER TABLE chat_message DROP INDEX  idx_bl_conv_server;

CREATE INDEX  idx_bl_conv_hash ON chat_message (business_line, conversation_id, hash_code);
CREATE INDEX  idx_bl_sender_time ON chat_message (business_line, sender_id, created_at);
CREATE INDEX  idx_bl_shop_id ON chat_message (business_line, shop_id);
CREATE UNIQUE INDEX  uk_bl_conv_client ON chat_message (business_line, conversation_id, client_msg_id);
CREATE UNIQUE INDEX  uk_bl_conv_server ON chat_message (business_line, conversation_id, server_msg_id);

-- chat_conversation_member：删除 init 基线旧索引（2026-05-19 已建 idx_bl_*）
ALTER TABLE chat_conversation_member DROP INDEX  idx_conversation_member;
ALTER TABLE chat_conversation_member DROP INDEX  idx_member_active;
ALTER TABLE chat_conversation_member DROP INDEX  uq_conversation_member_active;

CREATE UNIQUE INDEX  uk_bl_conversation_member_active ON chat_conversation_member
  (business_line, conversation_id, member_type, member_id, left_at);

-- -----------------------------------------------------------------------------
-- 2026-05-21 chat_message.shop_id 允许为空：无店铺进线（公司级会话）消息与 chat_conversation.shop_id 一致为 NULL
-- -----------------------------------------------------------------------------
ALTER TABLE chat_message
  MODIFY COLUMN shop_id BIGINT NULL COMMENT '店铺ID（无店铺进线时为 NULL，与 chat_conversation.shop_id 一致）';
