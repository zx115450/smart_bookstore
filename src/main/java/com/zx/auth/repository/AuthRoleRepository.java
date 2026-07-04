package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthRole;
import com.zx.auth.entity.AuthUserRole;
import com.zx.auth.mapper.AuthRoleMapper;
import com.zx.auth.mapper.AuthUserRoleMapper;
import com.zx.auth.security.RoleConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthRoleRepository {

    private final AuthRoleMapper roleMapper;
    private final AuthUserRoleMapper userRoleMapper;

    public Optional<AuthRole> findByCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(roleMapper.selectOne(
                Wrappers.<AuthRole>lambdaQuery().eq(AuthRole::getCode, code)
        ));
    }

    public List<String> findRoleCodesByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return userRoleMapper.findRoleCodesByUserId(userId);
    }

    public void assignRoleIfAbsent(Long userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return;
        }
        AuthRole role = findByCode(roleCode).orElse(null);
        if (role == null) {
            return;
        }
        Long count = userRoleMapper.selectCount(
                Wrappers.<AuthUserRole>lambdaQuery()
                        .eq(AuthUserRole::getUserId, userId)
                        .eq(AuthUserRole::getRoleId, role.getId())
        );
        if (count != null && count > 0) {
            return;
        }
        AuthUserRole userRole = new AuthUserRole(userId, role.getId());
        userRole.setCreatedAt(LocalDateTime.now());
        userRoleMapper.insert(userRole);
    }

    public void ensureDefaultUserRole(Long userId) {
        assignRoleIfAbsent(userId, RoleConstants.USER);
    }
}
