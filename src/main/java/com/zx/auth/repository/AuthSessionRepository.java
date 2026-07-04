package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthSession;
import com.zx.auth.mapper.AuthSessionMapper;
import com.zx.auth.service.AuthSessionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthSessionRepository {
    private final AuthSessionMapper mapper;
    private final AuthUserRepository userRepository;
    private final AuthSessionCacheService sessionCacheService;

    public Optional<AuthSession> findByRefreshTokenJti(String jti) {
        if (jti == null) {
            return Optional.empty();
        }

        Optional<AuthSession> cached = sessionCacheService.getByRefreshTokenJti(jti);
        if (cached.isPresent()) {
            return attachUser(cached.get());
        }

        AuthSession session = mapper.selectOne(
                Wrappers.<AuthSession>lambdaQuery().eq(AuthSession::getRefreshTokenJti, jti)
        );
        if (session == null) {
            return Optional.empty();
        }
        sessionCacheService.cache(session);
        return attachUser(session);
    }

    public Optional<AuthSession> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }

        Optional<AuthSession> cached = sessionCacheService.getById(id);
        if (cached.isPresent()) {
            return attachUser(cached.get());
        }

        AuthSession session = mapper.selectById(id);
        if (session == null) {
            return Optional.empty();
        }
        sessionCacheService.cache(session);
        return attachUser(session);
    }

    public void revokeById(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        AuthSession session = mapper.selectById(sessionId);
        if (session == null || session.getRevokedAt() != null) {
            return;
        }
        session.setRevokedAt(LocalDateTime.now());
        save(session);
    }

    public void revokeAllByUserId(Long userId) {
        if (userId == null) {
            return;
        }
        List<AuthSession> activeSessions = mapper.selectList(
                Wrappers.<AuthSession>lambdaQuery()
                        .eq(AuthSession::getUserId, userId)
                        .isNull(AuthSession::getRevokedAt)
        );
        LocalDateTime now = LocalDateTime.now();
        mapper.update(null, Wrappers.<AuthSession>lambdaUpdate()
                .eq(AuthSession::getUserId, userId)
                .isNull(AuthSession::getRevokedAt)
                .set(AuthSession::getRevokedAt, now)
                .set(AuthSession::getUpdatedAt, now));
        activeSessions.forEach(sessionCacheService::evict);
    }

    public AuthSession save(AuthSession session) {
        String oldJti = null;
        if (session.getId() != null) {
            AuthSession existing = mapper.selectById(session.getId());
            if (existing != null) {
                oldJti = existing.getRefreshTokenJti();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        if (session.getId() == null) {
            if (session.getCreatedAt() == null) {
                session.setCreatedAt(now);
            }
            session.setUpdatedAt(now);
            mapper.insert(session);
        } else {
            session.setUpdatedAt(now);
            mapper.updateById(session);
        }

        if (session.getRevokedAt() != null) {
            sessionCacheService.evict(session.getId(), oldJti != null ? oldJti : session.getRefreshTokenJti());
        } else {
            if (oldJti != null && !oldJti.equals(session.getRefreshTokenJti())) {
                sessionCacheService.evictByJti(oldJti);
            }
            sessionCacheService.cache(session);
        }
        return session;
    }

    private Optional<AuthSession> attachUser(AuthSession session) {
        userRepository.findById(session.getUserId()).ifPresent(session::setUser);
        return Optional.of(session);
    }
}
