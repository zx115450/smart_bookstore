package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.mapper.AuthUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    public Map<Long, AuthUser> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return mapper.selectList(
                Wrappers.<AuthUser>lambdaQuery().in(AuthUser::getId, ids)
        ).stream().collect(Collectors.toMap(AuthUser::getId, user -> user, (left, right) -> left));
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

    public long countAll() {
        Long count = mapper.selectCount(Wrappers.emptyWrapper());
        return count == null ? 0 : count;
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
