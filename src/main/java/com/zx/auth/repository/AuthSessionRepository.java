package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthSession;
import com.zx.auth.mapper.AuthSessionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthSessionRepository {
    private final AuthSessionMapper mapper;
    private final AuthUserRepository userRepository;

    public Optional<AuthSession> findByRefreshTokenJti(String jti) {
        if (jti == null) {
            return Optional.empty();
        }
        AuthSession session = mapper.selectOne(
                Wrappers.<AuthSession>lambdaQuery().eq(AuthSession::getRefreshTokenJti, jti)
        );
        if (session == null) {
            return Optional.empty();
        }
        userRepository.findById(session.getUserId()).ifPresent(session::setUser);
        return Optional.of(session);
    }

    public AuthSession save(AuthSession session) {
        LocalDateTime now = LocalDateTime.now();
        if (session.getId() == null) {
            if (session.getCreatedAt() == null) {
                session.setCreatedAt(now);
            }
            session.setUpdatedAt(now);
            mapper.insert(session);
            return session;
        }
        session.setUpdatedAt(now);
        mapper.updateById(session);
        return session;
    }
}
