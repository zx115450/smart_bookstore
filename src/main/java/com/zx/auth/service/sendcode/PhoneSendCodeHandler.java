package com.zx.auth.service.sendcode;

import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.service.AuthRedisService;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PhoneSendCodeHandler implements SendCodeHandler {
    private final AuthRedisService authRedisService;

    @Value("${auth.verification-code-ttl-seconds:300}")
    private long verificationCodeTtlSeconds;

    @Override
    public boolean supports(String loginType) {
        return "phone_code".equals(loginType);
    }

    @Override
    public Map<String, Object> handle(SendCodeRequest req, String clientIp) {
        String code = String.format("%06d", (int) (Math.random() * 1_000_000));
        authRedisService.saveVerificationCode(
                req.getLoginType(),
                req.getScene(),
                req.getTarget(),
                AuthService.sha256Hex(code)
        );

        Map<String, Object> res = new HashMap<>();
        res.put("expiresIn", verificationCodeTtlSeconds);
        return res;
    }
}
