package com.zx.ai.exception;

public class AiException extends RuntimeException {

    private final int code;

    public AiException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static AiException unavailable(String message) {
        return new AiException(5001, message == null ? "AI 服务暂时不可用" : message);
    }

    public static AiException rateLimited() {
        return new AiException(5002, "请求过于频繁，请稍后再试");
    }

    public static AiException sessionExpired() {
        return new AiException(5003, "会话不存在或已过期");
    }

    public static AiException badRequest(String message) {
        return new AiException(5004, message);
    }
}
