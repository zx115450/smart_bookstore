package com.zx.auth.service.sendcode;

import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.service.AuthRedisService;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendCodeHandler implements SendCodeHandler {
    private final AuthRedisService authRedisService;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String mailFrom;

    @Value("${auth.verification-code-ttl-seconds:300}")
    private long verificationCodeTtlSeconds;

    @Override
    public boolean supports(String loginType) {
        return "qq_email_code".equals(loginType);
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

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(req.getTarget());
        message.setSubject("验证码");
        message.setText("您的验证码是: " + code);
        mailSender.send(message);
        log.info("Sent email verification code to {}", req.getTarget());

        Map<String, Object> res = new HashMap<>();
        res.put("expiresIn", verificationCodeTtlSeconds);
        return res;
    }
}
