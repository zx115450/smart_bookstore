package com.zx.auth.service.sendcode;

import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.entity.AuthVerificationCode;
import com.zx.auth.repository.AuthVerificationCodeRepository;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailSendCodeHandler implements SendCodeHandler {
    private final AuthVerificationCodeRepository codeRepo;
    private final JavaMailSender mailSender;
    @Value("${spring.mail.username}")
    private String mailFrom;
    @Override
    public boolean supports(String loginType) {
        return "qq_email_code".equals(loginType);
    }

    @Override
    public Map<String, Object> handle(SendCodeRequest req, String clientIp) {
        String code = String.format("%06d", (int)(Math.random() * 1_000_000));
        //在数据库存放了这个验证码的信息
        AuthVerificationCode c = new AuthVerificationCode();
        c.setLoginType(req.getLoginType());
        c.setTarget(req.getTarget());
        c.setScene(req.getScene());
        c.setCodeHash(AuthService.sha256Hex(code));
        c.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        c.setSendIp(clientIp);
        codeRepo.save(c);
        //发送验证码
        // mailSender.send(message);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(req.getTarget());
        message.setSubject("验证码");
        message.setText("您的验证码是: " + code);
        mailSender.send(message);
        log.info("Sent email code {} to {}", code, req.getTarget());

        Map<String, Object> res = new HashMap<>();
        res.put("expiresIn", 300);

        return res;
    }
}

