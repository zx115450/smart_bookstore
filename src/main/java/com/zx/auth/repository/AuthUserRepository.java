package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthUserRepository {
    private final AuthUserMapper mapper;

    public Optional<AuthUser> findByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                Wrappers.<AuthUser>lambdaQuery().eq(AuthUser::getUsername, username)
        ));
    }

    public Optional<AuthUser> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<AuthUser> findByIdForUpdate(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectByIdForUpdate(id));
    }

    public boolean deductBalance(Long userId, BigDecimal amount) {
        if (userId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }
        return mapper.deductBalance(userId, amount) > 0;
    }

    public AuthUser save(AuthUser user) {
        LocalDateTime now = LocalDateTime.now();
        if (user.getId() == null) {
            if (user.getCreatedAt() == null) {
                user.setCreatedAt(now);
            }
            user.setUpdatedAt(now);
            mapper.insert(user);
            return user;
        }
        user.setUpdatedAt(now);
        mapper.updateById(user);
        return user;
    }
}
