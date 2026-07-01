package com.zx.auth.repository;

import com.zx.auth.entity.AuthLoginAudit;
import com.zx.auth.mapper.AuthLoginAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class AuthLoginAuditRepository {
    private final AuthLoginAuditMapper mapper;

    public AuthLoginAudit save(AuthLoginAudit audit) {
        if (audit.getId() == null && audit.getCreatedAt() == null) {
            audit.setCreatedAt(LocalDateTime.now());
        }
        if (audit.getId() == null) {
            mapper.insert(audit);
            return audit;
        }
        mapper.updateById(audit);
        return audit;
    }
}
