package com.zx.ai.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

/**
 * AI 模块业务异常。
 */
public class AiException extends BusinessException {

    public AiException(int code, String message) {
        super(code, message);
    }

    /** 模型或上游不可用。 */
    public static AiException unavailable(String message) {
        return new AiException(ErrorCode.AI_SERVICE_UNAVAILABLE, message == null ? "AI 服务暂时不可用" : message);
    }

    /** 限流。 */
    public static AiException rateLimited() {
        return new AiException(ErrorCode.AI_RATE_LIMITED, "请求过于频繁，请稍后再试");
    }

    /** 会话失效，前端应清除本地 sessionId。 */
    public static AiException sessionExpired() {
        return new AiException(ErrorCode.AI_SESSION_EXPIRED, "会话不存在或已过期");
    }

    /** 入参校验失败。 */
    public static AiException badRequest(String message) {
        return new AiException(ErrorCode.AI_BAD_REQUEST, message);
    }
}
