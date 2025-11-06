package com.treasurehunt.chat.framework.core.websocket.mvc.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.treasurehunt.chat.framework.core.websocket.distributed.session.WsSession;

import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;

import java.io.IOException;
import java.io.Serializable;

/**
 * WebSocket响应实体 - 框架层面的WebSocket协议元数据封装
 * 类似Spring MVC的ResponseEntity，负责WebSocket协议层封装
 * 不包含业务协议字段，框架通用性强
 */
public class WsResponseEntity<T> implements Serializable {
    
    /**
     * WebSocket帧类型选项
     * 对应WebSocket协议中的Opcode字段
     */
    private WebSocketFrameType frameType;
    
    /**
     * 是否作为最终帧发送
     * 对应WebSocket协议中的FIN字段
     */
    private Boolean finalFrame;
    
    /**
     * WebSocket扩展头信息
     * 用于自定义WebSocket协议元数据
     */
    private WsHeaders headers;
    
    /**
     * 业务数据载荷
     * Controller层返回的业务数据，可以是任意格式
     */
    private T body;
    
    private static final long serialVersionUID = 1L;
    
    public WsResponseEntity() {
        this.frameType = WebSocketFrameType.TEXT;
        this.finalFrame = true;
    }
    
    public WsResponseEntity(T body) {
        this();
        this.body = body;
    }
    
    public WsResponseEntity(WebSocketFrameType frameType, T body) {
        this();
        this.frameType = frameType;
        this.body = body;
    }
    
    // Getters and Setters
    public WebSocketFrameType getFrameType() {
        return frameType;
    }
    
    public void setFrameType(WebSocketFrameType frameType) {
        this.frameType = frameType;
    }
    
    public Boolean getFinalFrame() {
        return finalFrame;
    }
    
    public void setFinalFrame(Boolean finalFrame) {
        this.finalFrame = finalFrame;
    }
    
    public WsHeaders getHeaders() {
        return headers;
    }
    
    public void setHeaders(WsHeaders headers) {
        this.headers = headers;
    }
    
    public T getBody() {
        return body;
    }
    
    public void setBody(T body) {
        this.body = body;
    }
    
    /**
     * 直接发送到WsSession（支持本地和远程会话）
     * 这是推荐的方法，支持分布式部署
     */
    public void invokeSend(WsSession wsSession) throws IOException {
        switch (this.frameType) {
            case TEXT:
                sendAsTextFrame(wsSession);
                break;
            case BINARY:
                sendAsBinaryFrame(wsSession);
                break;
            case PING:
                sendAsPingFrame(wsSession);
                break;
            case PONG:
                sendAsPongFrame(wsSession);
                break;
            case CLOSE:
                sendAsCloseFrame(wsSession);
                break;
            default:
                sendAsTextFrame(wsSession);
                break;
        }
    }
    
    /**
     * 发送文本帧
     */
    private void sendAsTextFrame(WsSession session) throws IOException {
        if (body != null) {
            ObjectMapper mapper = new ObjectMapper();
            String jsonMessage = mapper.writeValueAsString(body);
            TextMessage textMessage = new TextMessage(jsonMessage);
            applyFrameHeaders(textMessage);
            session.sendMessage(textMessage);
        }
    }
    
    /**
     * 发送二进制帧
     */
    private void sendAsBinaryFrame(WsSession session) throws IOException {
        if (body != null) {
            byte[] binaryData = convertToBinaryData(body);
            BinaryMessage binaryMessage = new BinaryMessage(binaryData);
            applyFrameHeaders(binaryMessage);
            session.sendMessage(binaryMessage);
        }
    }
    
    /**
     * 发送Ping帧
     */
    private void sendAsPingFrame(WsSession session) throws IOException {
        // WebSocket Ping帧通常不需要数据
        BinaryMessage pingMessage = new BinaryMessage(new byte[0]);
        applyFrameHeaders(pingMessage);
        session.sendMessage(pingMessage);
    }
    
    /**
     * 发送Pong帧
     */
    private void sendAsPongFrame(WsSession session) throws IOException {
        // WebSocket Pong帧通常不需要数据
        BinaryMessage pongMessage = new BinaryMessage(new byte[0]);
        applyFrameHeaders(pongMessage);
        session.sendMessage(pongMessage);
    }
    
    /**
     * 发送关闭帧
     */
    private void sendAsCloseFrame(WsSession session) throws IOException {
        // WebSocket Close帧
        BinaryMessage closeMessage = new BinaryMessage(new byte[0]);
        applyFrameHeaders(closeMessage);
        session.sendMessage(closeMessage);
    }
    
    /**
     * 应用WebSocket帧头信息
     */
    private void applyFrameHeaders(Object message) {
        // 这里可以根据WsHeaders设置WebSocket扩展头信息
        // 例如压缩类型、编码类型等
        if (headers != null) {
            // 如果Spring WebSocket支持设置自定义头信息，可以在这里设置
            // 目前Spring WebSocket框架限制了这一功能，所以这里保留接口
        }
        
        // 处理finalFrame标志
        if (!Boolean.TRUE.equals(finalFrame)) {
            // 可以在这里设置分片消息的标记
            // Spring WebSocket会自动处理FIN标志
        }
    }
    
    /**
     * 将对象转换为二进制数据
     */
    private byte[] convertToBinaryData(Object data) {
        if (data instanceof byte[]) {
            return (byte[]) data;
        }
        
        // 序列化为JSON后再转为字节数组
        try {
            ObjectMapper mapper = new ObjectMapper();
            String jsonString = mapper.writeValueAsString(data);
            return jsonString.getBytes("UTF-8");
        } catch (Exception e) {
            return data.toString().getBytes();
        }
    }
    
    /**
     * 创建成功的文本响应
     */
    public static <T> WsResponseEntity<T> ok(T body) {
        return new WsResponseEntity<>(WebSocketFrameType.TEXT, body);
    }
    
    /**
     * 创建成功的二进制响应
     */
    public static <T> WsResponseEntity<T> binary(T body) {
        return new WsResponseEntity<>(WebSocketFrameType.BINARY, body);
    }
    
    /**
     * 创建带头信息的响应
     */
    public static <T> WsResponseEntity<T> withHeaders(WsHeaders headers, T body) {
        WsResponseEntity<T> response = new WsResponseEntity<>(WebSocketFrameType.TEXT, body);
        response.setHeaders(headers);
        return response;
    }
    
    @Override
    public String toString() {
        return "WsResponseEntity{" +
                "frameType=" + frameType +
                ", finalFrame=" + finalFrame +
                ", headers=" + headers +
                ", body=" + body +
                '}';
    }
    
    /**
     * WebSocket帧类型枚举
     * 对应WebSocket协议的标准opcode
     */
    public enum WebSocketFrameType {
        CONTINUATION(0x0),   // 延续帧
        TEXT(0x1),           // 文本帧  
        BINARY(0x2),         // 二进制帧
        CLOSE(0x8),          // 连接关闭帧
        PING(0x9),           // Ping帧
        PONG(0xA);           // Pong帧
        
        private final int opcode;
        
        WebSocketFrameType(int opcode) {
            this.opcode = opcode;
        }
        
        public int getOpcode() {
            return opcode;
        }
    }
    
    /**
     * WebSocket头信息类
     * 用于封装WebSocket协议的扩展头信息
     */
    public static class WsHeaders {
        private String compressionType;
        private String encodingType;
        private String extension;
        
        public WsHeaders() {}
        
        public WsHeaders(String compressionType, String encodingType) {
            this.compressionType = compressionType;
            this.encodingType = encodingType;
        }
        
        // Getters and Setters
        public String getCompressionType() { return compressionType; }
        public void setCompressionType(String compressionType) { this.compressionType = compressionType; }
        
        public String getEncodingType() { return encodingType; }
        public void setEncodingType(String encodingType) { this.encodingType = encodingType; }
        
        public String getExtension() { return extension; }
        public void setExtension(String extension) { this.extension = extension; }
        
        @Override
        public String toString() {
            return "WsHeaders{" +
                    "compressionType='" + compressionType + '\'' +
                    ", encodingType='" + encodingType + '\'' +
                    ", extension='" + extension + '\'' +
                    '}';
        }
    }
}
