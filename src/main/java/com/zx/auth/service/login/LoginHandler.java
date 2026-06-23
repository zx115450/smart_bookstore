package com.zx.auth.service.login;

import com.zx.auth.dto.LoginRequest;
import com.zx.auth.entity.AuthUser;

/**
 * Strategy interface for different login handlers (password, phone/email code, etc.).
 */
public interface LoginHandler {
    /**
     * Whether this handler supports the given loginType string
     */
    boolean supports(String loginType);

    /**
     * Perform login and return the authenticated/created AuthUser on success, or null on failure.
     */
    AuthUser handle(LoginRequest req, String clientIp);
}

