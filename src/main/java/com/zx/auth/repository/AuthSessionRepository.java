package com.zx.auth.repository;

import com.zx.auth.entity.AuthSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {
    Optional<AuthSession> findByRefreshTokenJti(String jti);
}

