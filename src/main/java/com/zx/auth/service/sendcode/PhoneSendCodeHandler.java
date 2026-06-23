package com.zx.auth.service.sendcode;

import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.entity.AuthVerificationCode;
import com.zx.auth.repository.AuthVerificationCodeRepository;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PhoneSendCodeHandler implements SendCodeHandler {
    private final AuthVerificationCodeRepository codeRepo;

    @Override
    public boolean supports(String loginType) {
        return "phone_code".equals(loginType);
    }

    @Override
    public Map<String, Object> handle(SendCodeRequest req, String clientIp) {
        String code = String.format("%06d", (int)(Math.random() * 1_000_000));
        AuthVerificationCode c = new AuthVerificationCode();
        c.setLoginType(req.getLoginType());
        c.setTarget(req.getTarget());
        c.setScene(req.getScene());
        c.setCodeHash(AuthService.sha256Hex(code));
        c.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        c.setSendIp(clientIp);
        codeRepo.save(c);

        Map<String, Object> res = new HashMap<>();
        res.put("expiresIn", 300);
        res.put("code", code); // demo only
        return res;
    }
}

