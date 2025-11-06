# 自定义框架 WebSocket Spring MVC 使用方法

## 概述

本框架是一个类似 Spring MVC 的 WebSocket 框架，支持单机和分布式部署,包名分为两大类,一个mvc,一个distributed，通过 SPI 机制实现可插拔的会话管理和实例注册。

## 核心特性

- **类似 Spring MVC 的注解驱动**：`@WsRequestMapping`、`@WsRequestBody`
- **自动协议封装**：`WsResponseEntity` 自动处理 WebSocket 协议元数据
- **跨实例支持**：通过 SPI 机制支持 Redis + Nacos 分布式部署
- **可插拔架构**：支持自定义会话注册和实例注册实现

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.macro.mall</groupId>
    <artifactId>mall-chat-framework</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 配置框架

```yaml
# application.yml
mall:
  chat:
    framework:
      cross-instance:
        enabled: true                    # 启用跨实例功能
        session-registry-type: redis     # 会话注册类型
        instance-registry-type: nacos    # 实例注册类型
```

### 3. 编写 Controller

```java
@RestController
public class ChatController {
    
    @WsRequestMapping("sendMessage")
    public RplySendResultPayload handleSendMessage(@WsRequestBody SendMessageRequest request) {
        // 直接返回业务对象，框架自动处理协议封装
        return RplySendResultPayload.builder()
            .clientMsgId(request.getClientMsgId())
            .serverMsgId(12345L)
            .uiState("SUCCESS")
            .build();
    }
    
    @WsRequestMapping("pullMessage")
    public PullMessageRequest pullMessage(@WsRequestBody PullMessageRequest request) {
        // 返回拉取的消息列表
        return PullMessageRequest.builder()
            .conversationId(request.getConversationId())
            .messageList(getMessages(request.getConversationId()))
            .build();
    }
}
```

## 注解说明

### @WsRequestMapping

用于映射 WebSocket 消息处理方法，类似 Spring MVC 的 `@RequestMapping`。

```java
@WsRequestMapping("sendMessage")
public ResponsePayload handleSendMessage(@WsRequestBody SendMessageRequest payload) {
    return responsePayload;
}
```

### @WsRequestBody

用于标记需要自动转换的 payload 参数，类似 Spring MVC 的 `@RequestBody`。

```java
@WsRequestMapping("sendMessage")
public void handleSendMessage(@WsRequestBody SendMessageRequest payload, WebSocketSession session) {
    // payload 会自动转换为 SendMessageRequest 类型
}
```

## 响应处理

### 自动响应

框架会自动将 Controller 返回的业务对象封装为 WebSocket 协议格式：

```java
@WsRequestMapping("sendMessage")
public RplySendResultPayload handleSendMessage(@WsRequestBody SendMessageRequest request) {
    // 返回业务对象
    return resultPayload;
}

// 框架自动发送：
// {
//   "frameType": "TEXT",
//   "finalFrame": true,
//   "body": {
//     "clientMsgId": "msg123",
//     "serverMsgId": 456,
//     "uiState": "SUCCESS"
//   }
// }
```

### 手动控制协议

如果需要控制 WebSocket 协议元数据，可以返回 `WsResponseEntity`：

```java
@WsRequestMapping("sendMessage")
public WsResponseEntity<RplySendResultPayload> handleSendMessage(@WsRequestBody SendMessageRequest request) {
    RplySendResultPayload data = processMessage(request);
    
    // 手动控制协议元数据
    return WsResponseEntity.binary(data)  // 发送二进制帧
        .withHeaders(WsHeaders.builder()
            .compressionType("gzip")
            .build());
}
```

## 跨实例部署

### 架构说明

框架支持分布式部署，通过以下组件实现跨实例通信：

- **SessionRegistry**：管理 `sessionId -> instanceId` 映射（默认 Redis）
- **InstanceRegistry**：管理实例注册和发现（默认 Nacos）
- **Redis Stream**：跨实例消息传递

### 配置示例

```yaml
# 分布式配置
mall:
  chat:
    framework:
      cross-instance:
        enabled: true
        session-registry-type: redis
        instance-registry-type: nacos

# Redis 配置
spring:
  redis:
    host: localhost
    port: 6379

# Nacos 配置
spring:
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
```

### 跨实例发送

```java
@WsRequestMapping("sendMessage")
public RplySendResultPayload handleSendMessage(@WsRequestBody SendMessageRequest request) {
    // 处理消息
    RplySendResultPayload result = processMessage(request);
    
    // 跨实例发送给目标用户
    WsSession targetSession = distributedSessionManager.getSession(targetUserId);
    WsResponseEntity.ok(result).sendTo(targetSession);
    
    return result;
}
```

## SPI 扩展

### 自定义会话注册

实现 `SessionRegistry` 接口：

```java
public class CustomSessionRegistry implements SessionRegistry {
    
    @Override
    public void registerSession(String userId, String sessionId, String instanceId) {
        // 自定义注册逻辑
    }
    
    @Override
    public String getInstanceId(String userId) {
        // 自定义查询逻辑
        return instanceId;
    }
    
    // 实现其他方法...
}
```

在 `META-INF/services/com.macro.mall.chat.framework.core.spi.SessionRegistry` 中注册：

```
com.example.CustomSessionRegistry
```

### 自定义实例注册

实现 `InstanceRegistry` 接口：

```java
public class CustomInstanceRegistry implements InstanceRegistry {
    
    @Override
    public void registerInstance(String instanceId, String instanceInfo) {
        // 自定义实例注册逻辑
    }
    
    @Override
    public Set<String> getActiveInstances() {
        // 自定义实例发现逻辑
        return activeInstances;
    }
    
    // 实现其他方法...
}
```

在 `META-INF/services/com.macro.mall.chat.framework.core.spi.InstanceRegistry` 中注册：

```
com.example.CustomInstanceRegistry
```

## AOP 切面实现机制

框架使用 AOP 切面自动拦截所有 WebSocketHandler 的生命周期方法：

```java
@Aspect
@Component
@ConditionalOnProperty(name = "websocket.distributed.enabled", havingValue = "true")
public class DistributedWebSocketAspect {
    
    @After("execution(* org.springframework.web.socket.WebSocketHandler.afterConnectionEstablished(..))")
    public void afterConnectionEstablished(JoinPoint joinPoint) {
        // 自动注册分布式会话
    }
    
    @After("execution(* org.springframework.web.socket.WebSocketHandler.afterConnectionClosed(..))")
    public void afterConnectionClosed(JoinPoint joinPoint) {
        // 自动注销分布式会话
    }
    
    @After("execution(* org.springframework.web.socket.WebSocketHandler.handleTransportError(..))")
    public void handleTransportError(JoinPoint joinPoint) {
        // 传输错误时只记录日志，不注销会话
        // 因为连接可能仍然有效，只有真正关闭时才注销
    }
}
```

**重要说明**：
- 使用 AOP 切面方式，避免与业务 `WebSocketConfigurer` 冲突
- **框架依赖风险**：本框架通过 AOP 切面维护 `sessionId -> instanceId` 映射，如果 Spring 升级导致切点表达式失效，框架可能无法正常工作
- 切点表达式：`execution(* org.springframework.web.socket.WebSocketHandler.afterConnectionEstablished(..))`
- **会话生命周期管理**：只在连接建立时注册，连接关闭时注销，传输错误不注销会话
- **升级注意事项**：Spring 版本升级后，需要验证 AOP 切面是否正常工作，必要时调整切点表达式

## 框架依赖风险

### Spring 版本兼容性

本框架通过 AOP 切面自动维护分布式会话管理，存在以下依赖风险：

1. **切点表达式依赖**：框架使用固定的切点表达式拦截 WebSocketHandler 方法
2. **Spring 升级风险**：如果 Spring 升级导致切点表达式失效，分布式会话管理将无法工作
3. **验证方法**：升级后检查日志中是否有 "分布式会话注册成功" 等日志输出

### 风险缓解措施

1. **版本锁定**：建议锁定 Spring 版本，避免频繁升级
2. **测试验证**：升级前在测试环境验证 AOP 切面是否正常工作
3. **监控告警**：监控分布式会话注册/注销日志，及时发现异常

### 失效表现

**重要**：切面失效时**不会**在启动时报错，因为：
- 切面类本身会正常加载（`@Component` 注解）
- 切点表达式失效只是不会匹配到目标方法
- 应用可以正常启动，但分布式会话管理功能静默失效

**检测方法**：
1. 检查日志：连接建立时没有 "分布式会话注册成功" 日志
2. 功能测试：跨实例消息发送失败
3. Redis 检查：`ws:session:*` 键没有新增记录

## 配置选项

### 完整配置示例

```yaml
mall:
  chat:
    framework:
      cross-instance:
        enabled: true                    # 是否启用跨实例功能
        session-registry-type: redis     # 会话注册类型：redis, local
        instance-registry-type: nacos    # 实例注册类型：nacos, local
      
      # WebSocket 配置
      websocket:
        max-message-size: 8192          # 最大消息大小
        max-session-idle-timeout: 30000 # 会话空闲超时
        compression-enabled: true       # 是否启用压缩
```

### 单机模式

```yaml
mall:
  chat:
    framework:
      cross-instance:
        enabled: false  # 禁用跨实例功能，使用单机模式
```

## 最佳实践

### 1. 消息设计

```java
// 请求消息
@Data
public class SendMessageRequest {
    private String clientMsgId;
    private String conversationId;
    private String content;
    private String type;
}

// 响应消息
@Data
@Builder
public class RplySendResultPayload {
    private String clientMsgId;
    private Long serverMsgId;
    private String uiState;
    private Long timestamp;
}
```

### 2. 错误处理

```java
@WsRequestMapping("sendMessage")
public RplySendResultPayload sendMessage(@WsRequestBody SendMessageRequest request) {
    try {
        return processMessage(request);
    } catch (Exception e) {
        // 返回错误响应
        return RplySendResultPayload.builder()
            .clientMsgId(request.getClientMsgId())
            .uiState("FAILED")
            .build();
    }
}
```

### 3. 性能优化

- 使用 `@WsRequestBody` 进行参数绑定，避免手动解析
- 返回业务对象而不是 `WsResponseEntity`，让框架自动处理
- 合理设计消息结构，避免过大的消息体

## 故障排除

### 常见问题

1. **跨实例消息发送失败**
   - 检查 Redis 连接配置
   - 确认目标实例的收件箱是否正常消费

2. **实例注册失败**
   - 检查 Nacos 连接配置
   - 确认实例 ID 唯一性

3. **消息序列化失败**
   - 确保业务对象实现 `Serializable`
   - 检查 Jackson 配置

### 日志配置

```yaml
logging:
  level:
    com.macro.mall.chat.framework: DEBUG
    org.springframework.web.socket: INFO
```

## 版本兼容性

- **Spring Boot**: 2.7+
- **Java**: 8+
- **Redis**: 5.0+
- **Nacos**: 2.0+

## 贡献指南

欢迎提交 Issue 和 Pull Request 来改进框架。

## 许可证

MIT License
