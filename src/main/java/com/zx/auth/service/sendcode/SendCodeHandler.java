package com.zx.auth.service.sendcode;

import com.zx.auth.dto.SendCodeRequest;

import java.util.Map;

public interface SendCodeHandler {
    boolean supports(String loginType);

    Map<String, Object> handle(SendCodeRequest req, String clientIp);
}

