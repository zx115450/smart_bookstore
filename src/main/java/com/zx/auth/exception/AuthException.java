package com.zx.auth.exception;

import com.zx.common.exception.BusinessException;
import com.zx.common.exception.ErrorCode;

/**
 * 认证模块业务异常（验证码、登录、OAuth 等）。
 */
public class AuthException extends BusinessException {

    public AuthException(int code, String message) {
        super(code, message);
    }

    public static AuthException invalidParam(String message) {
        return new AuthException(ErrorCode.AUTH_INVALID_PARAM, message);
    }

    public static AuthException sendCodeLimited(String message) {
        return new AuthException(ErrorCode.AUTH_SEND_CODE_LIMITED, message);
    }

    public static AuthException loginLocked(int lockMinutes) {
        return new AuthException(ErrorCode.AUTH_LOGIN_LOCKED,
                "登录失败次数过多，请" + lockMinutes + "分钟后再试");
    }

    public static AuthException oauthStateRequired() {
        return new AuthException(ErrorCode.AUTH_INVALID_PARAM, "state required");
    }
}
