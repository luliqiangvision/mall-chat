---
description: 商城聊天服务（mall-chat）开发规范
alwaysApply: true
---

# 开发规范

## 代码规范文件同步（必须）

本仓库编码规范分散在多处，**不同 AI/IDE 工具读取不同文件**（如根目录 `AGENTS.md`、`.cursor/rules/*.mdc`）。**任意一处规范发生增删改时，须同步更新所有同类规范文件**，保持语义一致，禁止长期内容分叉。

**当前必维护清单：** `AGENTS.md`、`.cursor/rules/mall-development-standards.mdc`。

1. **Redis key 与分布式锁 key 集中治理（必须）**：普通 Redis 数据 key 前缀须集中维护在 `mall-common` 的 `RedisKeyConstants`，分布式锁 Redis key 前缀须集中维护在 `mall-common` 的 `RedisLockKeyConstants`，禁止在业务类、Service、Controller、MQ/Stream 消费者里零散硬编码。新增 key 时要写清业务域、用途、TTL/续期语义；Redis 锁集群拆分时以锁 key 前缀为迁移范围，按业务域逐步切换，禁止按机器或整个集群一次性盲切。
2. Controller 层只做入参校验与调用 Service，不写工具逻辑。
3. 注释要用中文；AI 返回给用户的说明性内容也用中文；使用命令行时避免中文乱码。
4. 异常按业务场景处理，非必要不吞异常；`catch` 里必须打印日志。
5. SQL 变更追加在 `doc/modify.sql` 最底部，按顺序追加，不要插队。
6. 涉及业务线的表与接口需带 `business_line`，与现有机制保持一致。
7. 仅修改与当前任务相关的代码，不做无关重构。
8. 涉及中文文件批量处理，禁止 PowerShell 读写，只用编辑器工具或 UTF-8 的 Node 脚本文件。
