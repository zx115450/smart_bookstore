package com.zx.auth.repository;

import com.zx.auth.entity.AuthVerificationCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AuthVerificationCodeRepository extends JpaRepository<AuthVerificationCode, Long> {
    List<AuthVerificationCode> findByTargetAndSceneAndLoginTypeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(String target, String scene, String loginType, LocalDateTime now);
}

