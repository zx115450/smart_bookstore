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
public class EmailCodeLoginHandler implements LoginHandler {
    private final AuthVerificationCodeRepository codeRepo;
    private final AuthUserIdentityRepository identityRepo;
    private final AuthUserRepository userRepo;

    @Override
    public boolean supports(String loginType) {
        return "qq_email_code".equals(loginType);
    }

    @Override
    public AuthUser handle(LoginRequest req, String clientIp) {
        String scene = "login";
        List<AuthVerificationCode> codes = codeRepo.findByTargetAndSceneAndLoginTypeAndUsedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(req.getTarget(), scene, "qq_email_code", LocalDateTime.now());
        if (codes.isEmpty()) return null;
        AuthVerificationCode c = codes.get(0);
        if (!c.getCodeHash().equals(AuthService.sha256Hex(req.getCode()))) return null;
        // mark used
        c.setUsedAt(LocalDateTime.now());
        codeRepo.save(c);
        // find user by identity (email)
        Optional<AuthUserIdentity> ident = identityRepo.findByIdentityTypeAndIdentityValue("email", req.getTarget());
        AuthUser user;
        if (ident.isPresent()) {
            user = ident.get().getUser();
        } else {
            // auto-register minimal user with username=target
            AuthUser nu = new AuthUser();
            nu.setUsername(req.getTarget());
            nu = userRepo.save(nu);
            AuthUserIdentity ai = new AuthUserIdentity();
            ai.setUser(nu);
            ai.setIdentityType("email");
            ai.setIdentityValue(req.getTarget());
            ai.setVerified(true);
            ai.setIsPrimary(true);
            identityRepo.save(ai);
            user = nu;
        }
        return user;
    }
}

