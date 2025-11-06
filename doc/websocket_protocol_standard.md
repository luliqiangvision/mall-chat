# WebSocket聊天协议标准

## 实现对照表

### 已实现功能 ✅
- [x] 基础JSON消息格式
- [x] 客户/客服连接管理
- [x] 消息格式验证
- [x] 敏感词过滤
- [x] 速率限制
- [x] 连接数限制
- [x] 网关层面鉴权
- [x] RocketMQ消息持久化
- [x] 基础安全检查

### 待实现功能 ⏳
- [ ] 消息ID (msgId) 机制
- [ ] ACK确认机制
- [ ] 消息重发逻辑
- [ ] 图片消息支持
- [ ] 文件消息支持
- [ ] 系统消息类型
- [ ] 心跳机制
- [ ] 协议版本控制
- [ ] 更严格的输入验证
- [ ] 异常保护机制

### 安全增强待实现 🔒
- [ ] URL白名单检查
- [ ] 文件名安全检查
- [ ] 恶意内容检测
- [ ] 行为分析
- [ ] 解析器安全配置
- [ ] 异常处理优化
- [ ] **专业病毒扫描引擎集成** (当前只是基础文件格式检查)
- [ ] **动态病毒库更新机制**
- [ ] **多引擎病毒扫描支持**
- [ ] **云安全扫描API集成**

---

## 完整讨论总结

### 1. 我的初始分析

#### 当前系统分析
- 现有消息格式：JSON文本格式
- 系统特点：RocketMQ持久化、敏感词过滤、速率限制、连接管理
- 用户类型：区分客户与客服

#### 建议的改进方向
- 消息结构优化：统一JSON协议
- 可靠性机制：msgId、ACK确认、重发逻辑
- 系统消息类型：客服上下线、会话状态、排队通知

#### 需要讨论的问题
- 消息类型扩展：图片、文件、系统消息
- 可靠性：ACK与重发
- 文件处理：WebSocket消息中的文件信息
- 系统消息：系统通知类型
- 兼容性：平滑升级

### 2. 你的第一个问题：协议校验与病毒攻击

#### 我的回答
- 协议校验的作用：格式错误会被拒绝，不会进入业务逻辑，不会落库
- 安全检查：内容安全、用户ID格式验证、敏感词过滤
- 但无法完全防止病毒攻击：合法格式的恶意内容、文件上传攻击、社会工程学攻击

#### 建议的增强安全措施
- 内容安全增强：URL安全检查、文件类型验证、恶意内容检测
- 行为分析：异常发送频率、可疑行为模式、用户行为分析
- 多层防护：协议层、内容层、行为层、文件层

### 3. 别人的意见补充

#### 协议校验的作用
- 格式错误的消息不会进入业务逻辑
- 不会落库
- 降低业务层被恶意数据污染的风险

#### 潜在风险
- 协议层攻击：畸形数据包可能触发解析器漏洞
- 绕过校验：合法格式但恶意内容
- 流量攻击：DoS攻击
- 解析器漏洞：如fastjson历史漏洞

#### 当前系统的安全状况
- 已有协议格式验证
- 已有安全检查与内容过滤
- 已有连接限制与速率限制
- 网关层面已有鉴权机制

#### 需要关注的风险点
- 解析器安全：Jackson ObjectMapper版本与配置
- 输入验证：需要更严格的白名单限制
- 异常保护：解析异常应立即丢弃

#### 建议的增强措施
- 协议严格校验：使用Bean Validation
- 输入防御：URL白名单、文件名安全检查
- 异常保护：解析异常立即丢弃
- 多层防护：协议校验 + 鉴权 + 输入验证 + 异常保护

## 关键结论

### 1. 协议校验的作用
- 能拦截格式错误的消息，降低业务层风险
- 无法完全防止病毒攻击，仍需内容与行为层面的防护

### 2. 安全防护策略
- 采用多层防护：协议校验 + 鉴权 + 输入验证 + 异常保护
- 关注解析器安全、输入验证与异常处理

### 3. 系统改进方向
- 保持现有JSON格式，逐步扩展
- 先增加msgId与基础ACK
- 扩展消息类型，支持图片与文件
- 增加系统消息，完善状态通知
- 制定版本兼容策略

---

## 1. 消息格式规范

### 1.1 基础消息结构
```json
{
  "version": "1.0",
  "type": "chat|error|system|heartbeat",
  "content": "消息内容",
  "fromUserId": "发送者ID",
  "toUserId": "接收者ID",
  "timestamp": "2024-01-01 12:00:00",
  "fromUsername": "发送者用户名",
  "toUsername": "接收者用户名",
  "status": 0,
  "messageId": "消息唯一ID",
  "extras": {}
}
```

### 1.2 消息类型定义

#### 聊天消息 (chat)
```json
{
  "version": "1.0",
  "type": "chat",
  "content": "你好，我需要帮助",
  "fromUserId": "user123",
  "toUserId": "customer_service_001",
  "timestamp": "2024-01-01 12:00:00",
  "fromUsername": "张三",
  "toUsername": "客服小王",
  "status": 0,
  "messageId": "msg_123456789",
  "extras": {
    "messageType": "text|image|file",
    "fileUrl": "http://example.com/file.jpg",
    "fileName": "image.jpg",
    "fileSize": 1024
  }
}
```

#### 错误消息 (error)
```json
{
  "version": "1.0",
  "type": "error",
  "content": "消息发送失败",
  "fromUserId": "system",
  "toUserId": "user123",
  "timestamp": "2024-01-01 12:00:00",
  "status": 1,
  "messageId": "error_123456789",
  "extras": {
    "errorCode": "RATE_LIMIT_EXCEEDED",
    "errorMessage": "消息发送过于频繁，请稍后再试"
  }
}
```

#### 系统消息 (system)
```json
{
  "version": "1.0",
  "type": "system",
  "content": "客服已上线",
  "fromUserId": "system",
  "toUserId": "user123",
  "timestamp": "2024-01-01 12:00:00",
  "status": 0,
  "messageId": "system_123456789",
  "extras": {
    "systemType": "customer_service_online|customer_service_offline|connection_established"
  }
}
```

#### 心跳消息 (heartbeat)
```json
{
  "version": "1.0",
  "type": "heartbeat",
  "content": "ping",
  "fromUserId": "user123",
  "toUserId": "system",
  "timestamp": "2024-01-01 12:00:00",
  "status": 0,
  "messageId": "heartbeat_123456789",
  "extras": {}
}
```

## 2. 连接规范

### 2.1 连接URL格式
```
ws://gateway-host/ws/customer-service?userId={userId}&type={userType}
```

### 2.2 连接头信息
```
X-User-Id: 用户ID
X-User-Type: user|customer
X-Client-Version: 1.0
X-Platform: web|mobile|desktop
```

### 2.3 用户类型说明
- `user`: 普通用户
- `customer`: 客服人员

## 3. 错误码规范

### 3.1 连接错误
- `CONNECTION_FAILED`: 连接失败
- `AUTHENTICATION_FAILED`: 认证失败
- `INVALID_USER_TYPE`: 用户类型无效
- `CONNECTION_LIMIT_EXCEEDED`: 连接数超限

### 3.2 消息错误
- `MESSAGE_FORMAT_ERROR`: 消息格式错误
- `RATE_LIMIT_EXCEEDED`: 发送频率超限
- `CONTENT_FILTERED`: 内容被过滤
- `RECIPIENT_OFFLINE`: 接收者离线
- `MESSAGE_TOO_LONG`: 消息过长

### 3.3 系统错误
- `SYSTEM_ERROR`: 系统错误
- `SERVICE_UNAVAILABLE`: 服务不可用
- `DATABASE_ERROR`: 数据库错误

## 4. 安全规范

### 4.1 消息长度限制
- 文本消息: 最大10000字符
- 文件消息: 最大100MB

### 4.2 发送频率限制
- 每分钟最多60条消息
- 每小时最多1000条消息

### 4.3 连接限制
- 每用户最多5个连接
- 每IP最多15个连接
- 系统总连接数最多10000个

## 5. 扩展字段规范

### 5.1 extras字段说明
```json
{
  "extras": {
    "messageType": "text|image|file|voice|video",
    "fileUrl": "文件URL",
    "fileName": "文件名",
    "fileSize": 文件大小,
    "thumbnailUrl": "缩略图URL",
    "duration": 音视频时长,
    "replyTo": "回复的消息ID",
    "forwardFrom": "转发的消息ID",
    "tags": ["标签1", "标签2"],
    "priority": "normal|high|urgent",
    "readReceipt": true|false
  }
}
```

## 6. 测试用例

### 6.1 Postman测试步骤

1. **创建WebSocket连接**
   - URL: `ws://localhost/ws/customer-service`
   - Headers: `X-User-Id: test_user_001`, `X-User-Type: user`

2. **发送聊天消息**
   ```json
   {
     "version": "1.0",
     "type": "chat",
     "content": "测试消息",
     "toUserId": "customer_service_001"
   }
   ```

3. **发送心跳消息**
   ```json
   {
     "version": "1.0",
     "type": "heartbeat",
     "content": "ping"
   }
   ```

### 6.2 测试场景

1. **正常聊天流程**
   - 用户连接 → 发送消息 → 客服接收 → 客服回复 → 用户接收

2. **错误处理测试**
   - 发送格式错误消息
   - 发送超长消息
   - 发送频率超限

3. **连接管理测试**
   - 多连接测试
   - 断线重连测试
   - 心跳保活测试

## 7. 版本兼容性

### 7.1 版本号规则
- 主版本号: 不兼容的API修改
- 次版本号: 向下兼容的功能性新增
- 修订号: 向下兼容的问题修正

### 7.2 兼容性策略
- 客户端必须发送version字段
- 服务端根据version字段选择处理逻辑
- 旧版本客户端继续支持，但建议升级

## 8. 监控和日志

### 8.1 关键指标
- 连接数统计
- 消息发送成功率
- 消息延迟统计
- 错误率统计

### 8.2 日志格式
```json
{
  "timestamp": "2024-01-01 12:00:00",
  "level": "INFO|WARN|ERROR",
  "service": "mall-chat",
  "userId": "user123",
  "messageId": "msg_123456789",
  "action": "connect|disconnect|send|receive",
  "status": "success|failed",
  "duration": 100,
  "errorCode": "ERROR_CODE",
  "errorMessage": "错误描述"
}
```
