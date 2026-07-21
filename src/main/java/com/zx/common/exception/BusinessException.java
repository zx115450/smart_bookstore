package com.zx.common.exception;

/**
 * 统一业务异常基类。
 * <p>
 * 所有模块业务异常均应继承此类，通过 {@link #getCode()} 获取标准错误码，
 * 由 {@link GlobalExceptionHandler} 统一转换为 {@link com.zx.common.dto.ApiResponse}。
 */
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
