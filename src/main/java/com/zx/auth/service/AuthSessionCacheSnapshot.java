package com.zx.auth.service;

import com.zx.auth.entity.AuthSession;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Redis 缓存用的 Session 快照（不含 AuthUser，避免嵌套序列化）。
 */
public record AuthSessionCacheSnapshot(
        Long id,
        Long userId,
        String refreshTokenJti,
        String refreshTokenHash,
        long refreshExpiresAtEpoch,
        Long revokedAtEpoch,
        boolean rememberMe,
        String loginIp,
        long absoluteExpiresAtEpoch
) {

    static AuthSessionCacheSnapshot from(AuthSession session) {
        return new AuthSessionCacheSnapshot(
                session.getId(),
                session.getUserId(),
                session.getRefreshTokenJti(),
                session.getRefreshTokenHash(),
                session.getRefreshExpiresAt().toEpochSecond(ZoneOffset.UTC),
                session.getRevokedAt() == null ? null : session.getRevokedAt().toEpochSecond(ZoneOffset.UTC),
                Boolean.TRUE.equals(session.getRememberMe()),
                session.getLoginIp(),
                session.getAbsoluteExpiresAt().toEpochSecond(ZoneOffset.UTC)
        );
    }

    AuthSession toEntity() {
        AuthSession session = new AuthSession();
        session.setId(id);
        session.setUserId(userId);
        session.setRefreshTokenJti(refreshTokenJti);
        session.setRefreshTokenHash(refreshTokenHash);
        session.setRefreshExpiresAt(LocalDateTime.ofEpochSecond(refreshExpiresAtEpoch, 0, ZoneOffset.UTC));
        if (revokedAtEpoch != null) {
            session.setRevokedAt(LocalDateTime.ofEpochSecond(revokedAtEpoch, 0, ZoneOffset.UTC));
        }
        session.setRememberMe(rememberMe);
        session.setLoginIp(loginIp);
        session.setAbsoluteExpiresAt(LocalDateTime.ofEpochSecond(absoluteExpiresAtEpoch, 0, ZoneOffset.UTC));
        return session;
    }

    boolean isRefreshExpired() {
        return refreshExpiresAtEpoch <= LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }

    boolean isAbsoluteExpired() {
        return absoluteExpiresAtEpoch <= LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
    }
}
