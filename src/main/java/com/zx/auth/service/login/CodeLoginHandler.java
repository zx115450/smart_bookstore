package com.zx.auth.service.login;

import com.zx.auth.dto.LoginRequest;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.entity.AuthUserIdentity;
import com.zx.auth.entity.AuthVerificationCode;
import com.zx.auth.repository.AuthUserIdentityRepository;
import com.zx.auth.repository.AuthUserRepository;
import com.zx.auth.repository.AuthVerificationCodeRepository;
import com.zx.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CodeLoginHandler implements LoginHandler {
    private final AuthVerificationCodeRepository codeRepo;
    private final AuthUserIdentityRepository identityRepo;
    private final AuthUserRepository userRepo;

    @Override
    public boolean supports(String loginType) {
        // Deprecated: this generic handler is replaced by specific handlers. Do not report support.
        return false;
    }

    @Override
    public AuthUser handle(LoginRequest req, String clientIp) {
        // Deprecated generic handler should not be used.
        return null;
    }
}

