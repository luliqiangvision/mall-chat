package com.treasurehunt.chat.framework.core.websocket.distributed.delivery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SendResult {
    public enum SendCode {
        REMOTE_SENT,
        SELF_TARGET,
        POOL_UNAVAILABLE,
        CONNECT_FAIL,
        SERIALIZE_FAIL,
        UNKNOWN_ERROR
    }

    private boolean success;
    private SendCode code;
    private String message;
    private Throwable throwable;

    public static SendResult ok(SendCode code, String msg) {
        return new SendResult(true, code, msg, null);
    }

    public static SendResult fail(SendCode code, String msg, Throwable t) {
        return new SendResult(false, code, msg, t);
    }
}


