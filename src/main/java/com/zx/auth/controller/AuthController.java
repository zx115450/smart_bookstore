package com.zx.auth.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.dto.LoginRequest;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "code/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String,Object>> sendCode(@RequestBody SendCodeRequest req, HttpServletRequest servlet) {
        String ip = servlet.getRemoteAddr();
        Map<String,Object> data = authService.sendCode(req, ip);
        return ApiResponse.ok(data);
    }

    /*
    发送验证码：在 AuthService.sendCode() 中创建
     AuthVerificationCode 实体并通过 codeRepo.save(c) 持久化。
    验证码登录：通过 AuthVerificationCodeRepository.findByTarget...
    查找有效验证码；比对通过后修改 usedAt 并 save 回库；
    查 AuthUserIdentityRepository 找到用户，
    或通过 AuthUserRepository.save(...) 自动注册新用户。
    密码登录：通过 AuthUserRepository.findByUsername(account) 获取用户并比对 passwordHash。
    会话管理：AuthSession 实体写入 auth_session（sessionRepo.save(s)），
    并在刷新时通过 sessionRepo.findByRefreshTokenJti(jti) 查找。
    （预留）审计：AuthLoginAuditRepository 可用于写入登录成功/失败的审计记录（目前代码未统一写入）。
     */

    @PostMapping(value = "login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest servlet) {
        String ip = servlet.getRemoteAddr();
        try {
            var resp = authService.login(req, ip);
            if (resp == null) return ApiResponse.error(1001, "invalid credentials or code");
            return ApiResponse.ok(resp);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(1002, e.getMessage());
        }
    }

    @PostMapping(value = "refresh", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ApiResponse<LoginResponse> refresh(@RequestBody String refreshToken) {
        var resp = authService.refresh(refreshToken.trim());
        if (resp == null) return ApiResponse.error(2001, "invalid or expired refresh token");
        return ApiResponse.ok(resp);
    }
}


