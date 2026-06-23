package com.zx.auth.repository;

import com.zx.auth.entity.AuthLoginAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthLoginAuditRepository extends JpaRepository<AuthLoginAudit, Long> {
}

