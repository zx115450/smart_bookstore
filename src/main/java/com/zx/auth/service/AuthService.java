package com.zx.auth.service;

import com.zx.auth.config.AuthJwtProperties;
import com.zx.auth.dto.LoginRequest;
import com.zx.auth.dto.LoginResponse;
import com.zx.auth.dto.SendCodeRequest;
import com.zx.auth.entity.AuthLoginAudit;
import com.zx.auth.entity.AuthSession;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.repository.*;
import com.zx.auth.exception.AuthException;
import com.zx.auth.service.login.LoginHandlerFactory;
import com.zx.auth.service.sendcode.SendCodeHandlerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthUserRepository userRepo;
    private final AuthSessionRepository sessionRepo;
    private final AuthLoginAuditRepository auditRepo;
    private final AuthRoleRepository roleRepo;
    private final LoginHandlerFactory handlerFactory;
    private final SendCodeHandlerFactory sendCodeHandlerFactory;
    private final JwtService jwtService;
    private final AuthJwtProperties jwtProperties;
    private final AuthRedisService authRedisService;

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

    public Map<String, Object> sendCode(SendCodeRequest req, String clientIp) {
        authRedisService.checkSendCodeRateLimit(req.getTarget(), clientIp);
        var handler = sendCodeHandlerFactory.getHandler(req.getLoginType());
        return handler.handle(req, clientIp);
    }

    public LoginResponse login(LoginRequest req, String clientIp, String userAgent) {
        //  用 handle 校验 -> User
        // user -> 创建session(refreshToken) , accessToken

        String type = req.getLoginType();
        if (type == null) throw AuthException.invalidParam("loginType required");

        String loginKey = resolveLoginKey(req);
        //检查是否因为登录次数过多而禁止登录
        authRedisService.checkLoginAllowed(loginKey);

        var handler = handlerFactory.getHandler(type);
        AuthUser user = handler.handle(req, clientIp);
        if (user == null) {
            //登录失败 -> 验证码或者密码错误
            authRedisService.recordLoginFailure(loginKey);
            recordAudit(null, type, loginKey, false, "invalid credentials or code", clientIp, userAgent);
            return null;
        }
        //确保这个登录登录对象有一个默认的Role
        roleRepo.ensureDefaultUserRole(user.getId());
        //清除后端失败统计次数
        authRedisService.clearLoginFailure(loginKey);

        user.setLastLoginAt(LocalDateTime.now());
        //更新用户登录状态
        userRepo.save(user);

        String auditTarget = req.getTarget() != null ? req.getTarget() : loginKey;
        //登录日志的记录
        recordAudit(user.getId(), type, auditTarget, true, null, clientIp, userAgent);
        //
        return createSessionAndResponse(user, req.getRememberMe(), clientIp);
    }

    public LoginResponse refresh(String refreshToken) {
        if (refreshToken == null) return null;
        String[] parts = refreshToken.split(":", 2);
        if (parts.length != 2) return null;
        String jti = parts[0];
        //session的会话
        /*
            s.getRevokedAt()           → 是否已吊销
            s.getAbsoluteExpiresAt()   → 绝对过期
            s.getRefreshExpiresAt()    → Refresh 是否过期
            s.getRefreshTokenHash()    → 和 sha256(明文 refresh) 比对
            s.getUser() / getUserId()  → 签发新 Token、必要时 revokeAll
            s.getRememberMe()          → 新会话 refresh 时长
            s.getLoginIp()             → 带入新会话
            s.getAbsoluteExpiresAt()   → 轮换时继承绝对过期
         */
        Optional<AuthSession> so = sessionRepo.findByRefreshTokenJti(jti);
        if (so.isEmpty()) return null;
        AuthSession s = so.get();

        if (s.getRevokedAt() != null) {
            sessionRepo.revokeAllByUserId(s.getUserId());
            return null;
        }
        if (s.getAbsoluteExpiresAt() != null && s.getAbsoluteExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        if (s.getRefreshExpiresAt().isBefore(LocalDateTime.now())) return null;
        if (!s.getRefreshTokenHash().equals(sha256Hex(refreshToken))) return null;
        //作废现在的
        s.setRevokedAt(LocalDateTime.now());
        sessionRepo.save(s);

        List<String> roles = roleRepo.findRoleCodesByUserId(s.getUser().getId());
        //创建新的
        return createRotatedSession(s.getUser(), s.getRememberMe(), s.getLoginIp(), roles, s.getAbsoluteExpiresAt());
    }

    public void logout(String accessToken, String refreshToken) {
        if (accessToken != null && accessToken.startsWith("Bearer ")) {
            accessToken = accessToken.substring(7).trim();
        }
        if (accessToken != null && !accessToken.isBlank()) {
            try {
                JwtService.AccessTokenClaims claims = jwtService.parseAccessToken(accessToken);
                long ttl = Duration.between(Instant.now(), jwtService.getExpirationInstant(accessToken)).getSeconds();
                authRedisService.blacklistAccessToken(claims.jti(), ttl);
                sessionRepo.revokeById(claims.sessionId());
            } catch (JwtService.InvalidAccessTokenException | JwtService.TokenExpiredException ignored) {
                // access 已过期时仍尝试撤销 refresh
            }
        }

        if (refreshToken != null && !refreshToken.isBlank()) {
            String[] parts = refreshToken.split(":", 2);
            if (parts.length == 2) {
                sessionRepo.findByRefreshTokenJti(parts[0]).ifPresent(session -> sessionRepo.revokeById(session.getId()));
            }
        }
    }

    private LoginResponse createSessionAndResponse(AuthUser user, boolean rememberMe, String loginIp) {
        roleRepo.ensureDefaultUserRole(user.getId());
        List<String> roles = roleRepo.findRoleCodesByUserId(user.getId());
        return createRotatedSession(user, rememberMe, loginIp, roles, null);
    }

    private LoginResponse createRotatedSession(AuthUser user, boolean rememberMe, String loginIp, List<String> roles,
                                               LocalDateTime inheritedAbsoluteExpiresAt) {
        String jti = UUID.randomUUID().toString();
        String refresh = jti + ":" + UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime accessExp = now.plusSeconds(jwtProperties.getAccessExpireSeconds());

        LocalDateTime absoluteExp = inheritedAbsoluteExpiresAt != null
                ? inheritedAbsoluteExpiresAt
                : now.plusDays(jwtProperties.getSessionAbsoluteExpireDays());

        LocalDateTime slidingRefreshExp = now.plusDays(
                rememberMe ? jwtProperties.getRefreshExpireDaysRemember() : jwtProperties.getRefreshExpireDays()
        );
        LocalDateTime refreshExp = slidingRefreshExp.isAfter(absoluteExp) ? absoluteExp : slidingRefreshExp;
        //存储一个jti会话
        AuthSession s = new AuthSession();
        s.setUser(user);
        s.setRefreshTokenJti(jti);
        s.setRefreshTokenHash(sha256Hex(refresh));
        s.setAccessExpiresAt(accessExp);
        s.setRefreshExpiresAt(refreshExp);
        s.setAbsoluteExpiresAt(absoluteExp);
        s.setRememberMe(rememberMe);
        s.setLoginIp(loginIp);
        sessionRepo.save(s);
        //创建一个jwt
        String access = jwtService.createAccessToken(user.getId(), user.getUsername(), s.getId(), roles);

        LoginResponse resp = new LoginResponse();
        resp.setAccessToken(access);
        resp.setRefreshToken(refresh);
        resp.setExpireIn(jwtProperties.getAccessExpireSeconds());
        resp.setUserInfo(new LoginResponse.UserInfo(
                user.getId(),
                user.getUsername(),
                roles,
                user.getBalance() != null ? user.getBalance() : java.math.BigDecimal.ZERO
        ));
        return resp;
    }

    private String resolveLoginKey(LoginRequest req) {
        if ("password".equals(req.getLoginType())) {
            return req.getAccount();
        }
        if ("qq_oauth".equals(req.getLoginType())) {
            return req.getState();
        }
        return req.getTarget();
    }

    private void recordAudit(Long userId, String loginType, String target, boolean success,
                             String failReason, String ip, String userAgent) {
        AuthLoginAudit audit = new AuthLoginAudit();
        audit.setUserId(userId);
        audit.setLoginType(loginType);
        audit.setTarget(target);
        audit.setSuccess(success);
        audit.setFailReason(failReason);
        audit.setIp(ip);
        audit.setUserAgent(userAgent);
        auditRepo.save(audit);
    }
}
