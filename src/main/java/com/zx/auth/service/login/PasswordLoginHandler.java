package com.zx.auth.service.login;

import com.zx.auth.dto.LoginRequest;
import com.zx.auth.entity.AuthUser;
import com.zx.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PasswordLoginHandler implements LoginHandler {
    private final AuthUserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean supports(String loginType) {
        return "password".equals(loginType);
    }

    @Override
    public AuthUser handle(LoginRequest req, String clientIp) {
        if (req.getAccount() == null || req.getPassword() == null) return null;
        Optional<AuthUser> uo = userRepo.findByUsername(req.getAccount());
        if (uo.isEmpty()) return null;
        AuthUser u = uo.get();
        if (u.getPasswordHash() == null) return null;
        if (!passwordEncoder.matches(req.getPassword(), u.getPasswordHash())) return null;
        return u;
    }
}
