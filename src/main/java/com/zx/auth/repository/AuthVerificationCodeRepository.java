package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthVerificationCode;
import com.zx.auth.mapper.AuthVerificationCodeMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class AuthVerificationCodeRepository {
    private final AuthVerificationCodeMapper mapper;

    public List<AuthVerificationCode> findByTargetAndSceneAndLoginTypeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(
            String target, String scene, String loginType, LocalDateTime now) {
        return mapper.selectList(
                Wrappers.<AuthVerificationCode>lambdaQuery()
                        .eq(AuthVerificationCode::getTarget, target)
                        .eq(AuthVerificationCode::getScene, scene)
                        .eq(AuthVerificationCode::getLoginType, loginType)
                        .isNull(AuthVerificationCode::getUsedAt)
                        .gt(AuthVerificationCode::getExpiresAt, now)
                        .orderByDesc(AuthVerificationCode::getCreatedAt)
        );
    }

    public AuthVerificationCode save(AuthVerificationCode code) {
        if (code.getId() == null) {
            if (code.getCreatedAt() == null) {
                code.setCreatedAt(LocalDateTime.now());
            }
            mapper.insert(code);
            return code;
        }
        mapper.updateById(code);
        return code;
    }
}
