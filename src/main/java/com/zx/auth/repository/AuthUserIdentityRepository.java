package com.zx.auth.repository;

import com.zx.auth.entity.AuthUserIdentity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthUserIdentityRepository extends JpaRepository<AuthUserIdentity, Long> {
    Optional<AuthUserIdentity> findByIdentityTypeAndIdentityValue(String identityType, String identityValue);
}

