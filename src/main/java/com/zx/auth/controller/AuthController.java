package com.zx.auth.controller;

import com.zx.auth.dto.ApiResponse;
import com.zx.auth.dto.LoginRequest;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping(value = "code/send", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> sendCode(@RequestBody SendCodeRequest req, HttpServletRequest servlet) {
        String ip = servlet.getRemoteAddr();
        try {
            Map<String, Object> data = authService.sendCode(req, ip);
            return ApiResponse.ok(data);
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(1002, e.getMessage());
        }
    }

    @PostMapping(value = "login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<LoginResponse> login(@RequestBody LoginRequest req, HttpServletRequest servlet) {
        String ip = servlet.getRemoteAddr();
        String userAgent = servlet.getHeader(HttpHeaders.USER_AGENT);
        log.info("login request loginType={}", req.getLoginType());
        try {
            var resp = authService.login(req, ip, userAgent);
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

    @PostMapping("logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody(required = false) String refreshToken
    ) {
        authService.logout(authorization, refreshToken == null ? null : refreshToken.trim());
        return ApiResponse.ok(null);
    }
}
