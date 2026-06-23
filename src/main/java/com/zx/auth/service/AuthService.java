package com.zx.auth.service;

import com.zx.auth.dto.LoginRequest;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.entity.*;
import com.zx.auth.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import com.zx.auth.service.login.LoginHandlerFactory;
import com.zx.auth.service.sendcode.SendCodeHandlerFactory;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepo;
    private final AuthUserIdentityRepository identityRepo;
    private final AuthVerificationCodeRepository codeRepo;
    private final AuthSessionRepository sessionRepo;
    private final AuthLoginAuditRepository auditRepo;
    private final LoginHandlerFactory handlerFactory;
    private final SendCodeHandlerFactory sendCodeHandlerFactory;

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Map<String,Object> sendCode(SendCodeRequest req, String clientIp) {
        var handler = sendCodeHandlerFactory.getHandler(req.getLoginType());
        return handler.handle(req, clientIp);
    }

    public LoginResponse login(LoginRequest req, String clientIp) {
        String type = req.getLoginType();
        if (type == null) throw new IllegalArgumentException("loginType required");
        // Delegate to a login handler (factory + strategy)
        var handler = handlerFactory.getHandler(type);
        AuthUser user = handler.handle(req, clientIp);
        if (user == null) return null;
        return createSessionAndResponse(user, req.getRememberMe());
    }

    private LoginResponse createSessionAndResponse(AuthUser user, boolean rememberMe) {
        String access = "access:" + UUID.randomUUID();
        String jti = UUID.randomUUID().toString();
        String refresh = jti + ":" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExp = now.plusHours(2);
        LocalDateTime refreshExp = now.plusDays(rememberMe?30:7);

        AuthSession s = new AuthSession();
        s.setUser(user);
        s.setRefreshTokenJti(jti);
        s.setRefreshTokenHash(sha256Hex(refresh));
        s.setAccessExpiresAt(accessExp);
        s.setRefreshExpiresAt(refreshExp);
        s.setRememberMe(rememberMe);
        sessionRepo.save(s);

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refresh);
        resp.setExpireIn(7200);
        resp.setUserInfo(new LoginResponse.UserInfo(user.getId(), user.getUsername()));
        return resp;
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null) return null;
        String[] parts = refreshToken.split(":", 2);
        if (parts.length != 2) return null;
        String jti = parts[0];
        Optional<AuthSession> so = sessionRepo.findByRefreshTokenJti(jti);
        if (so.isEmpty()) return null;
        AuthSession s = so.get();
        if (s.getRevokedAt() != null) return null;
        if (s.getRefreshExpiresAt().isBefore(LocalDateTime.now())) return null;
        if (!s.getRefreshTokenHash().equals(sha256Hex(refreshToken))) return null;
        String access = "access:" + UUID.randomUUID();
        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refreshToken);
        resp.setExpireIn(7200);
        resp.setUserInfo(new LoginResponse.UserInfo(s.getUser().getId(), s.getUser().getUsername()));
        return resp;
    }
}


