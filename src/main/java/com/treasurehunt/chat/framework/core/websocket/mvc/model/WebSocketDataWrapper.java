package com.treasurehunt.chat.framework.core.websocket.mvc.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.io.Serializable;

/**
 * WebSocket消息包装器（泛型版本）
 * 对应Spring MVC的HttpServletRequest，封装WebSocket消息的请求和响应信息
 * 
 * 消息格式：
 * {
 *   "interfaceName": "sendMessage",
 *   "version": 1,
 *   "payload": { ... },
 *   "success": true,
 *   "errorMessage": null
 * }
 * 
 * | 阶段                  | 状态码体系                    | 示例               |
| ------------------- | ------------------------ | ---------------- |
| 握手阶段（HTTP）          | ✅ 使用 HTTP 状态码            | 101、400、403、500  |
| 连接通信阶段（WebSocket 帧） | ❌ 无状态码，需应用层自定义           | 自定义 `code` 字段    |
| 连接关闭阶段              | ✅ 有 WebSocket Close Code | 1000、1001、1011 等 |

 * @param <T> 载荷数据的类型，提供类型安全性
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketDataWrapper<T> implements Serializable {
    
        /**
         * 接口名称，用于路由到对应的处理器方法
         * 对应Spring MVC的URL路径
         */
        private String interfaceName;

        /**
         * 协议版本
         */
        private Integer version;

        /**
         * 消息载荷，具体的业务数据
         * 对应Spring MVC的RequestBody，使用泛型提供类型安全性
         */
        private T payload;

        /**
         * websocket状态码，这是我们自己自定义的，不是websocket协议本身的状态码
         */
        private String websocketCode;
        /**
         * 本次请求是否算成功
         */
        private boolean success;

        private String errorMessage;

        private static final long serialVersionUID = 1L;
    
    /**
     * 获取接口名称（用于路由）
     * 框架通过反射调用此方法
     */
    public String getInterfaceName() {
        return interfaceName;
    }
    
    /**
     * 获取消息载荷
     * 框架通过反射调用此方法
     */
    public T getPayload() {
        return payload;
    }
    
    /**
     * 设置接口名称
     */
    public void setInterfaceName(String interfaceName) {
        this.interfaceName = interfaceName;
    }
    
    /**
     * 设置协议版本
     */
    public void setVersion(Integer version) {
        this.version = version;
    }
    
    /**
     * 设置消息载荷
     */
    public void setPayload(T payload) {
        this.payload = payload;
    }
    
    /**
     * 获取协议版本
     */
    public Integer getVersion() {
        return version;
    }
    
    /**
     * 创建成功响应的静态工厂方法
     * 
     * @param interfaceName 接口名称
     * @param payload 载荷数据
     * @param <T> 载荷类型
     * @return 成功响应的WebSocketDataWrapper
     */
    public static <T> WebSocketDataWrapper<T> success(String interfaceName, T payload,String statusCode) {
        return WebSocketDataWrapper.<T>builder()
                .interfaceName(interfaceName)
                .version(1)
                .payload(payload)
                .success(true)
                .websocketCode(statusCode)
                .build();
    }
    
    /**
     * 创建失败响应的静态工厂方法
     * 
     * @param interfaceName 接口名称
     * @param errorMessage 错误信息
     * @param <T> 载荷类型
     * @return 失败响应的WebSocketDataWrapper
     */
    public static <T> WebSocketDataWrapper<T> failure(String interfaceName, String errorMessage,String statusCode) {
        return WebSocketDataWrapper.<T>builder()
                .interfaceName(interfaceName)
                .version(1)
                .success(false)
                .errorMessage(errorMessage)
                .websocketCode(statusCode)
                .build();
    }
    
    /**
     * 创建失败响应的静态工厂方法（带载荷）
     * 
     * @param interfaceName 接口名称
     * @param errorMessage 错误信息
     * @param payload 载荷数据
     * @param <T> 载荷类型
     * @return 失败响应的WebSocketDataWrapper
     */
    public static <T> WebSocketDataWrapper<T> failure(String interfaceName, String errorMessage,String statusCode,T payload) {
        return WebSocketDataWrapper.<T>builder()
                .interfaceName(interfaceName)
                .version(1)
                .payload(payload)
                .success(false)
                .errorMessage(errorMessage)
                .websocketCode(statusCode)
                .payload(payload)
                .build();
    }

    @Override
    public String toString() {
        return "WebSocketDataWrapper{" +
                "interfaceName='" + interfaceName + '\'' +
                ", version=" + version +
                ", payload=" + payload +
                ", success=" + success +
                ", websocketCode='" + websocketCode + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}
