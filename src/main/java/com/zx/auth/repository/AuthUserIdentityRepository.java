package com.zx.auth.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zx.auth.entity.AuthUserIdentity;
import com.zx.auth.mapper.AuthUserIdentityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AuthUserIdentityRepository {
    private final AuthUserIdentityMapper mapper;
    private final AuthUserRepository userRepository;

    public Optional<AuthUserIdentity> findByIdentityTypeAndIdentityValue(String identityType, String identityValue) {
        if (identityType == null || identityValue == null) {
            return Optional.empty();
        }
        AuthUserIdentity identity = mapper.selectOne(
                Wrappers.<AuthUserIdentity>lambdaQuery()
                        .eq(AuthUserIdentity::getIdentityType, identityType)
                        .eq(AuthUserIdentity::getIdentityValue, identityValue)
        );
        if (identity == null) {
            return Optional.empty();
        }
        userRepository.findById(identity.getUserId()).ifPresent(identity::setUser);
        return Optional.of(identity);
    }

    public AuthUserIdentity save(AuthUserIdentity identity) {
        LocalDateTime now = LocalDateTime.now();
        if (identity.getId() == null) {
            if (identity.getCreatedAt() == null) {
                identity.setCreatedAt(now);
            }
            identity.setUpdatedAt(now);
            mapper.insert(identity);
            return identity;
        }
        identity.setUpdatedAt(now);
        mapper.updateById(identity);
        return identity;
    }
}
